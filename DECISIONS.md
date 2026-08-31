# 決策紀錄

審查者會合理質疑的每一個選擇記在這裡。每一則寫三件事：選了什麼、當時還考慮過什麼、
這個取捨的代價是什麼。

每一則都在**做決定的當下**寫，寫在做出這個決定的同一個 commit 裡。事後回頭補寫，
會生出一個比真實情況整齊的版本——沒走的那條路正是記憶最先丟掉的東西。

---

## 1. 七個模組，而不是一個

**選了** —— `:app`、`:core:domain`、`:core:data`、`:core:ui`，以及一個畫面一個模組
（`:feature:feed`、`:feature:detail`、`:feature:saved`）。

**當時還考慮** —— 單一 `:app` 模組，內部用 package 分層。以這個規模的 app 來說那是
站得住腳的選擇，而且建置快得多。

**取捨** —— 模組邊界要付出建置設定的成本與一些樣板，建置也比單模組慢。換來的是
**domain 的純度由編譯器保證**：`:core:domain` 是純 Kotlin 模組、沒有 Android 相依，
所以 DTO 或 Compose 型別**不可能**流進去——會直接建置失敗。用 package 分層的話，
這件事只是一個慣例。

**這個保證只到這裡為止。** 編譯器擋住的是「Android 型別進 domain」，它擋不住
`:feature:*` 去相依 `:core:data`，也擋不住 feature 之間互相相依——那些只要有人改一行
Gradle 設定就成立了。剩下的部分由第 2 則的檢查把關；只講「相依方向由編譯器保證」
是把 Gradle 設定當成了不可變的事實。

**為什麼它是第一個被決定的** —— 模組邊界是唯一一個「拖越久越貴」的決定，因為在它
之前寫的每一個檔案事後都得搬家。

---

## 2. 相依規則寫成 Gradle 檢查，而不是寫在文件裡

**選了** —— 根 `build.gradle.kts` 的 `checkModuleDependencies`：把允許的相依邊列成
一張表，讀取每個模組實際宣告的 `ProjectDependency`，出現表外的邊就讓建置失敗。
它掛在 `check` 上，所以 `./gradlew build` 就會跑到，不需要額外步驟。

**當時還考慮**

- **只寫在文件裡，靠人審。** 成本零，但這正是第 1 則被質疑的地方——一條沒有任何機制
  能發現它被違反的規則，讀者無法判斷它是否真的在跑。
- **Konsist 架構測試。** 它擅長的是 source 層級的規則（package 結構、命名、import），
  而這裡要管的是**模組之間的相依**，那是 Gradle 才知道的事實。等有實際程式碼之後，
  Konsist 會用來管 source 層級的規則，兩者不衝突。

**取捨** —— 這張表是手寫的，新增模組時要記得更新它，忘了就會誤報。換來的是相依規則
從一句主張變成一道會失敗的檢查。

**驗證過它會失敗** —— 暫時讓 `:feature:feed` 相依 `:core:data`，檢查如預期報出
`:feature:feed must not depend on :core:data`，然後還原。**沒看過失敗的檢查不算檢查**，
這一點對 gate 與對測試是同一個標準。

---

## 3. 領域模型自己拒絕不合法的值，壞資料在邊界被丟掉

**選了** —— `ArticleId` 與 `ArticleItem` 在 `init` 用 `require` 守不變條件，違反就丟
`IllegalArgumentException`。相對的責任落在 `:core:data` 的 mapper：它逐筆決定一份回應
能不能變成領域物件，不能的那一筆丟掉，不讓它炸掉整頁。

**當時還考慮**

- **工廠回傳 `Result<ArticleItem>`。** 呼叫端被型別逼著處理失敗。代價是每個建立點都分岔成
  兩條路徑，而呼叫端仍然可以 `getOrThrow()` 把保證丟回去。
- **完全不驗，欄位放寬成 nullable。** 成本零，代價是每個畫面各自判斷「這個標題算不算空」，
  而且判斷會不一致。
- **在 UI 層擋。** 最晚的一道防線，等於承認領域物件不可信。

**取捨** —— `require` 讓「不合法的 `ArticleItem` 不存在」變成型別層級的事實，下游不必重複
檢查。代價是**這個保證只在邊界真的過濾時才成立**：mapper 若把 DTO 直接送進建構子而不處理
例外，一筆壞資料就會炸掉整頁。編譯器不會提醒這件事，所以它由 mapper 那個 commit 的測試
來守——一筆壞的、一筆好的，斷言只有壞的被丟掉。

## 4. 時間用 `java.time.Instant`

**選了** —— 領域模型的時間欄位是 `java.time.Instant`。minSdk 24 而 `java.time` 要到
API 26，所以各 Android 模組開 core library desugaring。

**當時還考慮**

- **直接放 API 給的字串。** 顯示時不必轉換，但 freshness 是對時間做算術，字串做不了；
  而且「怎麼讀給人看」是 UI 的決定，放進模型等於提早決定它。
- **`kotlin.time.Instant`（Kotlin 2.3 已提供）或 `kotlinx-datetime`。** 有跨平台需求時
  會是首選。這個專案只有 Android，多一層互通成本（Ktor、持久化、Compose 各自要轉）。

**取捨** —— 要多開 desugaring（已開）。另外 `java.time.Instant` 對 Compose 是外部型別、
推導不出 stable，直接當 composable 參數會讓它無法 skip；那要把型別列進
`compose_compiler_config.conf`。等它真的進到 UI 再做，現在寫下來免得那天忘了為什麼。

## 5. `ArticleId` 包的是 `String`，儘管 API 給的是整數

**選了** —— `@JvmInline value class ArticleId(String)`。

**當時還考慮** —— 裸 `Int`／`Long`（貼近來源的實際型別）、裸 `String`（少一個型別）。

**取捨** —— 異質 feed 進來之後，天氣與電影的 id 不是整數；統一成 String，saved 的 key
才只有一種形狀。代價有兩個：mapper 要 `toString()`，而且**不同來源的 `42` 會撞**。
真正跨來源的 key 需要帶 source namespace，但設計那個型別需要的資訊要等異質內容進場才有。
在那之前，`ArticleId` 只代表「文章這個來源裡的 id」。

## 6. 壞資料逐列處理，而不是整頁

**選了** —— 回應的 `results` 先當成未解碼的 `JsonElement` 收下，再逐列解碼成 DTO、逐列
試著變成 `ArticleItem`。變不成的那一列丟掉，**帶著原因**回到呼叫端
（`MappedArticles.droppedReasons`）。另外兩個選擇同樣重要：
畫面上「需要它才顯示得出來」的欄位（`url`、`news_site`、`published_at`）在 DTO 沒有預設值，
缺了就是壞列；而**整份回應少了 `results` 是錯誤，不是空頁**。

**當時還考慮**

- **直接宣告 `results: List<ArticleDto>`。** 少一層間接，程式碼最短。但 kotlinx 是整批
  解碼的：任何一列的 `id` 型別錯，整頁在 mapper 執行之前就失敗了——「一筆壞的不能炸掉
  整頁」這個契約根本不成立。這是第二個模型指出來的，我原本的版本就是這樣寫的。
- **一列壞就整頁失敗。** 最嚴格，也最容易解釋。但作業明說這是個 flaky API，
  一列壞資料換掉整個 feed 是使用者付的代價。
- **每個欄位都給預設值。** 最寬鬆，代價是壞資料被偽裝成「合法但空白的文章」，
  問題推給 UI，而 UI 沒有資訊可以判斷。

**取捨** —— 多了一層 `JsonElement` 的間接與每列一次的解碼成本。換來的是損失有界
（一列壞只少一列）、而且 empty 與 error 兩個畫面狀態分得開——作業明確要求這兩個要分開。

**還沒接上的地方** —— `droppedReasons` 目前沒有人讀。API 若哪天把欄位改名，
這個設計不會炸，只會**安靜地少資料**。所以那份原因清單必須有人看：
它接到哪裡（log、遙測、或畫面上的提示）是後面的 commit 要決定的事，現在先讓它不至於在
知道原因的那一刻就被丟掉。

## 7. 分頁跟著伺服器給的連結走，而不是自己算 offset

**選了** —— 第一頁用 `limit` 去要，之後每一頁都用上一頁回應裡的 `next` 連結原樣去要。
而且**只跟隨指回同一個集合的連結**（整段前綴比對，scheme＋host＋path 一起比）。

**當時還考慮**

- **自己算 offset（`offset += pageSize`）。** 最直覺，狀態也最好測——只是一個整數。
  但 feed 是活的：讀者在看的時候還有新文章在發布，於是第二頁會重複最後一筆，
  或讓某一筆永遠不出現。而如果 offset 是用「成功映射的筆數」加的，
  被丟掉的壞列還會讓偏移量繼續累積。
- **用 `published_at` 當 keyset 游標。** 真正穩的做法，但這個 API 不支援。

**取捨** —— 分頁狀態從一個整數變成一個不透明的字串：重試時不能重算，快取的 key 也比較醜。
換來的是「下一頁在哪」由伺服器回答，這個 app 不會是算錯的那一方。

**它解決不了的** —— 連結分頁**不提供 snapshot 一致性**。清單在兩次請求之間仍然會移動，
只是不再由客戶端製造誤差。真正要修需要 API 提供游標或時間邊界，它沒有。

**為什麼要驗證那個連結** —— `next` 是回應裡的字串，也就是外部輸入。
不驗證就跟隨，等於讓回應決定這個 app 下一個要打哪個 host。整段前綴比對讓
`https://api.spaceflightnewsapi.net.example.com/` 這種相似 host 過不了關——
只比對 host 開頭的寫法會過。

## 8. 網路層丟例外，型別化的失敗留在 repository 邊界

**選了** —— `:core:data` 的網路層用例外表達失敗（`expectSuccess = true`），
不在這一層轉成 `Result`。轉換點放在 repository：那裡才會把
offline／HTTP 錯誤／回應無法解讀 分成 UI 能用 `when` 窮舉的幾種。

**當時還考慮**

- **網路層就回傳 `Result<ArticlePage>`。** 呼叫端被型別逼著處理失敗。但 `Result` 的失敗
  仍然只是 `Throwable`，UI 還是得靠 `is IOException` 這種判斷去猜 offline，
  等於把問題往上搬而沒有解決。
- **一路用例外到 ViewModel。** 最少程式碼，但作業明確要求 error 與 offline 是**兩個**畫面
  狀態，而「哪一種例外算 offline」這個判斷散在 UI 層會不一致。

**取捨** —— 在 repository 出現之前，網路層的測試只能斷言 Ktor 自己的例外型別
（`ServerResponseException`），那是實作細節，第二個模型也指出了這一點。
這個代價是有期限的：repository 那個 commit 會把它換成領域的失敗型別，
屆時這一層的測試改成斷言轉換後的結果。

## 9. repository 不記得讀者讀到哪裡

**選了** —— `suspend fun articles(after: PageCursor?)`：游標由呼叫端傳進來，
repository 自己不持有「我在第幾頁」。失敗用 sealed 的 `FeedFailure` 回答，不再往上丟例外。

**當時還考慮**

- **有狀態的 repository**：內部持有 `Flow<List<ArticleItem>>` 與 `loadMore()`。
  這是 Now in Android 那一系的形狀，UI 只要收 flow 很省事。代價是**一份清單只有一個位置**：
  兩個畫面同時看同一個 repository 會互相把對方的清單移動掉；測試也得先把它推到某個狀態
  才能斷言下一步。
- **Paging 3**。它把載入、預抓、重試、錯誤與 UI 綁進一套框架合約。這份作業要考的正好是
  freshness 與失敗語意要怎麼自己定義——交給 Paging 之後，那些決定會變成「Paging 怎麼做」，
  而不是我怎麼想。

**取捨** —— 「已經載入的清單」要有人保管，而那個人變成 ViewModel。這是刻意的：
**讀者讀到哪裡**是畫面的狀態，不是資料的狀態。等離線快取進場時，快取仍然會住在
repository（那是資料的狀態），兩者不衝突。

**失敗為什麼分四種而不是一個訊息** —— 作業要求 loading／empty／error／offline 是四個
不同的畫面。畫面沒辦法從一個字串裡選出來，所以差異必須在還知道差異的那一層被固定下來，
也就是這裡。再上一層，「沒有網路」與「伺服器壞了」都只是一個空白畫面。

## 10. 畫面狀態是五個不同的型別，不是一個帶旗標的物件

**選了** —— `FeedUiState` 是 sealed interface：`Loading`／`Empty`／`Offline`／`Error`／
`Content`。ViewModel 持有分頁游標與已載入的清單；repository 不持有（見第 9 則）。

**當時還考慮**

- **一個 data class 帶欄位**：`articles: List` ＋ `isLoading: Boolean` ＋ `error: String?`。
  這是最常見的寫法，Compose 也好寫。代價是**寫得出沒有意義的狀態**——有錯誤又有文章、
  正在載入又有錯誤——而且每個 composable 都要自己決定那些組合該畫什麼，決定還會不一致。
- **把 offline 和 error 併成一個**：少一個分支。但作業明確要求這兩個要分開，
  而且它們對使用者的意義不同：offline 值得自動重試，500 不值得。

**取捨** —— sealed 型別讓「載入中同時有錯誤」這種狀態**寫不出來**，代價是加一個狀態要改
所有 `when`。這正是想要的：漏掉一個分支會編譯失敗，而不是在某個裝置上畫出空白。

**下一頁失敗是 `Content` 的一個欄位，不是一個狀態** —— 因為第四頁沒到而把前三頁換成錯誤
畫面，是拿讀者已經讀到的東西去懲罰他捲動。所以它是清單底部的一行字，不是整個畫面。
