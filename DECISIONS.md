# 決策紀錄

審查者會合理質疑的每一個選擇記在這裡。每一則寫三件事：選了什麼、當時還考慮過什麼、
這個取捨的代價是什麼。

每一則都在**做決定的當下**寫，寫在做出這個決定的同一個 commit 裡。事後回頭補寫，
會生出一個比真實情況整齊的版本——沒走的那條路正是記憶最先丟掉的東西。

---

## 1. 七個模組，而不是一個

> **現在是八個，而且 `:app` 的職責窄了一格**（第 31 則）：導覽獨立成 `:navigation`，
> `:app` 只剩 composition root 與 Android 進入點，一條 `:feature:*` 的邊都不再宣告。
> **下面不改寫**——它記的是當時的決定，而當時 `:app` 確實兼任導覽。

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

## 11. 導覽用 Navigation 3，而不是自己管一個 back stack

**選了** —— `NavDisplay` ＋ `rememberNavBackStack`，key 是 `@Serializable` 的型別。
文章 id 以 `String` 跨越導覽邊界，`ArticleId` 留在領域層。

**當時還考慮**

- **自己拿一個 `rememberSaveable` 的狀態切畫面。** 兩個畫面時這是最省的寫法，八行就好。
  但 saved 進來就是第三個畫面，而且 back stack 的還原（旋轉、行程被回收）要自己寫，
  那正是導覽函式庫存在的理由。
- **Navigation Compose（2.x）。** 成熟得多，但它的 route 是字串，參數要編碼進 URL，
  型別安全是後來補上去的。Nav3 的 back stack 就是一個可觀察的 `List<NavKey>`，
  這對「畫面狀態屬於畫面」這個立場（見第 9、10 則）是一致的。

**取捨** —— Nav3 還很新，API 會動：實測 `NavDisplay` 的 `onBack` 簽章就與手上的範例不同，
`entry` 也不是可以單獨 import 的函式而是 scope 的成員。換到穩定版之前，升級要當成一次
獨立的變更來驗。

**為什麼 id 是 String** —— 還原 back stack 靠序列化，而「寫進 Bundle 的東西長什麼樣」
是回到某個畫面的細節，不是領域的細節。`ArticleId` 在導覽的另一端才被重新造出來。

## 12. 存起來的文章是一個 JSON 檔，不是一個資料庫

**選了** —— `:core:data` 的 `FileSavedArticles`：整份清單存成一個 JSON 檔，每次變更整檔重寫，
記憶體裡一份 `StateFlow` 供 UI 觀察。磁碟上的形狀是自己的 `StoredArticle`，
不是 `ArticleItem`——領域模型不必知道自己會被寫下來。

**當時還考慮**

- **Room。** 大多數人的預設，而且以後要查詢、要分頁都現成。真正讓我不選它的是**測試**：
  Room 的記憶體資料庫需要 Android context，這個專案沒有 Robolectric，
  所以 DAO 的行為在 JVM 上測不到。用 Room 等於「加一個沒有測試的持久層」，
  而持久層正是壞掉最安靜的地方。
- **DataStore（Preferences 或 Proto）。** 比 Room 輕，但 Preferences 版存清單要自己序列化
  （等於現在這個做法多一層），Proto 版要引入 protobuf 工具鏈。
- **每篇文章一個檔。** 寫入便宜（只動一個檔），但列出清單要掃目錄、排序要讀每一個檔頭。

**取捨** —— 整檔重寫在清單變大時是 O(n) 的寫入。對「使用者親手存下來的閱讀清單」
這個量級（幾十到幾百篇）這不是問題；如果它變成快取整個 feed 的機制，這個選擇就錯了，
那時要換成 Room。**換掉的成本是這個檔案，不是呼叫端**——`SavedArticles` 介面在領域層，
實作換掉不影響任何畫面。

**讀取刻意寬容** —— 檔案壞掉（電池在寫入途中沒電）讀成空清單，並把原因留在
`lastProblem` 讓上層能說出來。丟掉閱讀清單很糟，但每次開啟都 crash 更糟；
而**安靜地**丟掉它，跟使用者自己清空的結果無法區分。

## 13. 「新鮮」是兩個數字，不是一個

**選了** —— `Freshness` 帶兩個時間窗：一般連線 15 分鐘，計費連線 1 小時。
`isStale(fetchedAt, now, metered)` 回答「該不該再問一次」。每一種內容的實際數字寫在
`Cadence` 裡，集中一處。

**為什麼是兩個** —— 作業要求「保持合理新鮮」與「不要浪費使用者的行動網路」，
這兩件事方向相反。用一個數字就得選邊：短了浪費資料，長了資料過期。
兩個數字讓它變成**同一個問題在兩種情境下的兩個答案**，而不是一個折衷。

**當時還考慮**

- **單一 TTL。** 最簡單，也最誠實地承認沒有處理計費網路那半個要求。
- **完全不快取、每次都抓。** 目前的行為，也是最浪費的。
- **只在 Wi-Fi 自動更新，行動網路一律要手動。** 更省，但一個開了 app 卻不更新的 feed
  會被當成壞掉；使用者不會知道那是節省。

**取捨** —— 兩個窗要有人維護，而且「計費」這件事要有地方問（Android 的
`ConnectivityManager`，之後接）。換來的是政策**可以被測試**：
`isStale` 是純函式，時鐘是參數。

**刻意寫死的兩個防呆** —— 計費的窗不得短於一般的窗（否則政策比沒有政策更耗資料），
以及窗不得為零。兩個都在建構子擋，因為它們不是設定錯誤，是邏輯錯誤。

**未來時間視為新鮮** —— 手機的時鐘會變（時區、校時、換日線）。把未來的時間戳當成錯誤，
結果是「在時鐘追上之前不停重抓」——恰好是最耗資料的行為。

**這一則只定義了政策，還沒有人用它。** 誰記錄 `fetchedAt`、誰問「現在是不是計費連線」，
是下一個 commit 的事。

## 14. 天氣是同一個清單裡的另一種 cell，不是另一種 `FeedItem`

**選了** —— `Weather` 是獨立的領域模型，有自己的 repository 介面；
`FeedUiState.Content` 多一個 `weather: Weather?` 欄位；畫面在同一個 `LazyColumn` 裡
用一個 `item {}` 畫出視覺上明顯不同的卡（不同的容器顏色、沒有圖片、一個大數字）。

**當時還考慮** —— `sealed interface FeedItem { ArticleItem, WeatherItem }`，
讓清單真的變成 `List<FeedItem>`。這是最「正統」的異質 feed 做法。

**為什麼沒選** —— 因為兩種內容**沒有共同的行為**。文章有 id、有分頁、可以存起來、
有內頁；天氣只有一筆讀數、不分頁、不存、沒有內頁。把它們塞進同一個 sealed type，
換來的是「清單只有一種型別」的整齊，付出的是：每一個用到它的地方都要 `when`，
而每一個 `when` 的天氣分支都在說「這裡不適用」。那不是抽象，是把巧合寫成型別。

**README 的順序早就寫過這件事**：異質性刻意排在最後，因為「在還沒有真實內容可供歸納
之前就先選定抽象，等於是對著猜測做設計」。真的做到這一步之後，能歸納的結論是
**它們不該共用型別**——而這個結論只有在兩邊都存在時才看得出來。

**代價** —— 第三種來源（電影）進來時要再問一次同樣的問題。如果那時候發現「電影和文章
共用夠多行為」（有 id、有清單、可以存），那就是 sealed type 真的該出現的時候，
而不是現在。

**freshness 各有各的節奏** —— 文章 15／60 分鐘，天氣 10／30 分鐘。
這正是作業提示的那件事（天氣以分鐘變、文章以小時變），也是「一個 TTL 給全部」擋不住的。

## 15. 天氣的地點寫死在 Taipei

**選了** —— 座標與名稱寫死，不要定位權限。

**當時還考慮** —— 用 `ACCESS_COARSE_LOCATION` 拿使用者所在地。

**取捨** —— 要一個定位權限只為了在 feed 頂端放一張卡，代價是權限對話框、
說明文案、拒絕後的路徑、以及「去設定開啟」的引導——四條使用者流程，
換一個大部分讀者不會注意到的細節。寫死的地點沒有這些，而且它對自己顯示的東西是誠實的
（卡片上就寫著 Taipei）。真的要做，這是個設定項，不是預設行為。

## 16. 分頁把視窗釘在一個時刻，而不是跟一排會動的東西算 offset

**選了** —— 第一頁的請求帶 `published_at_lte=<現在>`。這個條件會被伺服器寫進它產生的
每一個 `next` 連結，所以整個瀏覽過程都在讀「那一刻的那份清單」。下拉更新開一個新視窗。

**問題長什麼樣** —— SNAPI 用 `limit`／`offset` 分頁，而新文章是往**清單頂端**加的。
於是「從第 21 筆開始」這句話，在寫下它的那一刻與被使用的那一刻，指的不是同一批東西：

- 頂端插入 25 篇 → 第 21 筆變成畫面上已經有的那幾篇 → **請求發出去了，一篇都沒多**
- 頂端縮短 → 兩頁之間出現一個沒有人看得到的區間

**當時還考慮**

- **以 id 去重。** 這是先前的做法，它擋掉了重複造成的 `LazyColumn` 重複 key 當機——
  但那只是最外層的症狀，「捲了卻什麼都沒加」與「中間漏一段」都還在。
- **不快取游標，捲到底時先重抓第一頁。** 會讓分頁在讀者眼前重排，而且是在繞過問題。
- **快取整份 snapshot。** 一致性最好，但快取變大、寫入變頻繁，freshness 的語意要重想。

**取捨** —— 多一個查詢參數，換到 offset 重新有意義。代價是視窗一旦釘住，就**看不到之後
發布的文章**，直到讀者下拉更新——但那正是「更新」這個動作應該做的事，而且比讓清單在
腳下滑動好。

**它保證到什麼程度（第二個模型指出我原本寫得太滿）** —— 這是一個**時間截點**，不是伺服器
發的 snapshot。以下情況它擋不住：

- **裝置時鐘快**：快的那段時間內匯入的文章仍然符合截點，offset 還是會位移
- **裝置時鐘慢**：那段時間內的新文章這一輪看不到
- **回填**：帶著舊 `published_at` 事後匯入的文章會落在截點之內
- 伺服器端的刪除、編輯，以及排序上的平手

所以正確的說法是**大幅降低**而不是**消除**。`distinctBy { it.id }` 因此留著——
它擋不住遺漏，但擋得住重複造成的 `LazyColumn` 重複 key。真正要「零重複零遺漏」，
需要伺服器提供 snapshot 或 keyset cursor，那不在這個 API 的能力範圍內。

**快取的游標是對的** —— 存下來的第一頁連同它的游標一起存，所以從快取還原的畫面
繼續往下捲時，待在**當初抓它的那一代**。這裡換成新視窗反而會讓清單自我矛盾。

**這一則怎麼來的** —— 我原本從第一原理推導 offset 的行為，提出了「不要快取游標」這種
繞路的解法。是使用者說「這邊應該是看它的 API 怎麼設計」才去查的，一查就發現
`published_at_lte` 一直都在。**先讀 API 再設計，不要先設計再遷就 API。**

## 17. 天氣讀數也寫進檔案，因為「能撐多久」跟「還在不在」是兩件事

**選了** —— 最後一筆讀數存成 `weather.json`，跟文章快取、閱讀清單同一種形狀。
新鮮度政策照舊（`Cadence.WEATHER`：一般 10 分鐘、計費連線 30 分鐘）。

**原本錯在哪** —— 讀數只存在欄位裡，理由寫在註解上：「上次開 app 時的溫度不值得顯示」。
那句話回答的是**值不值得顯示**，而那個問題新鮮度政策已經有答案了。欄位實際回答的是
另一個問題——**系統回收 app 之後它還在不在**——而答案是「永遠不在」。那不是一個政策，
是一個後果。

被系統回收後兩分鐘重開，那筆讀數照我們自己的標準還新鮮，卻要再付一次請求。

**在裝置上看得到** —— 關掉網路、`am force-stop`、重開：文章從快取回來了，**天氣卡整個不見**。
這是實際跑出來才看到的，單元測試不會告訴我這件事。

**快取放在建構子參數，不是裝飾器** —— 跟文章不一樣。文章的快取回答的是「請求失敗時
顯示什麼」，所以它包在任何一個 repository 外面（`CachingArticles`）；天氣只有一個來源，
而讀數本身就是被保存的東西，中間再加一層就只是一層。

**取捨** —— 多一個檔案、多一次啟動時的讀檔。換到的是一次啟動不再無條件付費。

## 18. 檔案讀不回來是「沒有」，不是「爆炸」——而且要接對例外

**選了** —— 三個檔案存放器（文章快取、天氣讀數、閱讀清單）在讀回來時，把
`SerializationException`、`IllegalArgumentException`、`DateTimeException` 都接住，
順序也排對。快取整份作廢，閱讀清單只丟掉讀不回來的那一列。

**錯在哪** —— 原本只接 `IllegalArgumentException`，理由是「domain 的 `require` 丟這個」。
但時間戳是 `Instant.parse` 解的，它丟 **`DateTimeParseException`**——那是 `DateTimeException`，
`RuntimeException` 的子類，**不是** `IllegalArgumentException`。所以一個壞掉的時間戳
會直接穿過三層防護，從 `read()` 裡飛出去，把呼叫它的 coroutine 一起帶走。

同一個錯我寫了三次，因為我是照著第一個檔案複製的。

**還有一個順序錯誤** —— `SerializationException` 本身**就是** `IllegalArgumentException`
的子類，而我把 `IllegalArgumentException` 排在前面。那個 branch 從來沒有執行過，
它的訊息從來沒有被寫出來過。註解上還寫著相反的話。

**閱讀清單為什麼是丟一列不是丟整份** —— 快取掉了只是一次請求；閱讀清單是讀者自己存的。
一列讀不回來不是丟掉其餘那些的理由。這跟網路那層對壞掉的 row 的處理是同一個原則。

**這一則是 Codex 找到的。** 它讀 diff 時直接指著那個 catch 問「你確定 `Instant.parse`
丟的是這個嗎」。六個測試寫下去全紅。**這就是用第二個模型的理由**——同一個模型再讀一次，
會再確認一次自己寫下的假設。

## 19. DI 用 Hilt，而 `:core:domain` 一個註解都沒有

**選了** —— Hilt（`@Module` / `@Provides` / `@HiltViewModel`），所有繫結集中在
`:core:data` 的一個 `DataModule`，`:app` 只負責套用主題與 `@HiltAndroidApp`。

**當時還考慮**

- **完全手寫的建構子注入 + 一個 composition root。** 這個規模其實夠用，而且零建置成本。
  放棄的原因是 ViewModel：Android 自己會建立它們，手寫就得寫一個 `ViewModelProvider.Factory`
  再自己把相依性接進去——那段樣板正好就是 Hilt 存在的理由。
- **Koin。** 設定簡單，但繫結錯誤要**跑到那一行**才會炸。Hilt 在編譯期就檢查完整張圖，
  而這個專案的核心主張是「gate 綠才算完成」——把錯誤往編譯期推是同一個方向。

**取捨** —— KSP 讓建置變慢，而且產生的程式碼在 stack trace 裡很吵。換到的是
「相依圖不完整就編不過」。

**`:core:domain` 保持乾淨** —— 它是純 Kotlin 模組，**沒有 Hilt、沒有 `javax.inject`**。
domain 不該知道有人在幫它組裝。這也是它能用純 JVM 測試的原因之一。

**dispatcher 也走注入** —— `Dispatchers.IO` 只出現在 `DataModule` 裡三次，
三個檔案存放器收的都是建構子參數 `io: CoroutineDispatcher`。測試因此能塞
`UnconfinedTestDispatcher` 進去，不需要任何 dispatcher 的全域替換。

---

## 20. Compose，而且畫面狀態只有一個入口

**選了** —— 全 Compose，沒有任何 XML layout、沒有 `Fragment`。Material 3。

**為什麼不是 Views** —— 這個 app 的整個 UI 是「一個狀態物件 → 一個畫面」。
Views 要處理的是「從 A 狀態變成 B 狀態要改哪幾個 view」，而那正是
`FeedUiState` 有五個型別（`DECISIONS.md` 10）之後不必再處理的事。
另外 Navigation 3 只有 Compose 版本，選了它就不再有選擇。

**當時還考慮**

- **Views + ViewBinding。** 成熟、工具鏈穩、`RecyclerView` 的差異更新久經考驗。
  放棄是因為異質 feed：`RecyclerView` 要 view type、要 `ViewHolder`、要 `DiffUtil`，
  而 `LazyColumn` 裡那是 `item { }` 和 `items() { }` 兩行。
- **混合（Compose 包在 Fragment 裡）。** 只有在既有專案裡才划算，這裡是全新的。

**取捨（而且是真的痛）** —— **Compose 的畫面測不到**，除非上裝置或引入 Robolectric。
Views 至少還能用 JVM 測 presenter 對 view 介面的呼叫。這就是為什麼這個專案的
138 個測試沒有一個碰到 composable，也是 `AI_USAGE.md` 裡第二個模型連續指出的
最弱一環。**這個代價是選 Compose 換來的，寫在這裡才誠實。**

---

## 21. 並行：suspend 一路到底，沒有 scope 是自己開的

**選了** —— 結構化並行。三條規則：

1. **repository 全部是 `suspend`**，沒有回傳 `Flow` 的請求、沒有 callback、沒有 RxJava。
   唯一的 `Flow` 是 `SavedArticles.saved`，因為那真的是一個會變動的清單。
2. **只有 `viewModelScope`。** 沒有 `GlobalScope`，沒有自己建的 `CoroutineScope`。
   畫面消失時，還在飛的請求跟著被取消——這是預設行為而不是要記得寫的收尾。
3. **`Dispatchers.IO` 只出現在檔案邊界**，而且是注入的（見 #19）。
   網路不需要切 dispatcher：Ktor 的 `suspend` 本來就不佔執行緒。

**`CancellationException` 一定要重新丟出** —— 三個 repository 的 catch 鏈第一條都是
`catch (cancelled: CancellationException) { throw cancelled }`。這是
`catch (e: Exception)` 在 coroutine 裡最容易犯的錯：吞掉取消訊號，
畫面已經走了，工作還在跑。

**寫入用 `Mutex` 串起來** —— `FileSavedArticles` 的每次 save／forget 是
「讀檔 → 改 → 寫檔」，兩個同時發生會互相覆蓋。用 `Mutex` 而不是 `synchronized`，
因為中間那段是 `suspend`——`synchronized` 區塊裡不能掛起。

**當時還考慮**

- **repository 回傳 `Flow<ArticlesResult>`。** 讓快取先發、網路後發是很漂亮的模型，
  但分頁的「載入下一頁」是一次性的問題，用串流表達會讓「這一次要不要強制重抓」
  變成串流的參數，比 `suspend fun articles(force: Boolean)` 難讀得多。
- **在 repository 裡開自己的 scope 做背景更新。** 那會讓「誰在等這個結果」變得不明確，
  而取消就再也不是免費的了。

**取捨** —— 畫面消失時請求一定被取消，包括那些「反正快好了」的。
代價是讀者切出去再切回來可能要重抓一次；換到的是沒有任何工作能活得比它的畫面久。

## 22. 三層：DTO → domain model → UI state

> **這一則的結構還成立，表格裡的型別名有三個已經不在了**（第 28、30 則）：
> `FeedUiState` 被刪掉，UI 那格現在是 `ArticleRow`／`WeatherHeadline` 加一個**推導出來的**
> `FeedPhase`；磁碟那格的 `StoredArticle`／`StoredPage` 隨文章快取一起消失，現在只剩
> `StoredWeather` 和 `SavedArticleEntity`。三層本身沒有變——**不改寫下面的表格**，
> 因為它記的是當時的決定，而當時那些型別是真的存在。

**選了** —— 資料在到達畫面之前經過三種形狀，每一種只回答一個問題：

| 層 | 型別 | 回答什麼 | 住在哪 |
|---|---|---|---|
| **DTO** | `ArticleDto`、`ArticlePageDto`、`ForecastDto`（網路）；`StoredArticle`、`StoredPage`、`StoredWeather`（磁碟） | 對方的格式長什麼樣 | `:core:data`，全部 `private`／`internal` |
| **Domain model** | `ArticleItem`、`Weather`、`ArticlesResult`、`FeedFailure` | 這個 app 認為世界是什麼樣子 | `:core:domain`，純 Kotlin |
| **UI state** | `FeedUiState` + `ArticleRow`／`WeatherHeadline`；`DetailUiState` + `ArticleView`；`SavedUiState` + `SavedRow` | 讀者**看到什麼字** | 各自的 feature 模組 |

**原本缺的是第三層。** 狀態的**形狀**是 UI 自己的（五個型別，`DECISIONS.md` 10），
但它**裝的**是 domain 物件：一串 `ArticleItem`、一個 `Weather`、一個 `FeedFailure`。
於是「`NASA · 31 Aug, 21:14` 這行字怎麼組出來」、「500 該說哪一句」、
「文章不見了要不要給重試按鈕」——全部在 composable 裡決定。

**為什麼那是問題** —— 這個專案**沒有畫面測試**（Compose 要裝置或 Robolectric，
`DECISIONS.md` 20 記了這個代價）。所以任何搬進 composable 的決定，就等於搬到了
**沒有任何自動化檢查搆得到的地方**。實際後果：日期在中文裝置上顯示成 `31 8月, 21:14`，
138 個測試全綠，是**跑起來用眼睛看**才發現的。

現在那些決定在 ViewModel 的邊界完成，是普通的函式回傳普通的字串，ViewModel 自己的
測試就直接斷言那些字。**測不到的東西變少了，而不是多寫了一層樣板。**

**取捨**

- 多三個檔案、多一次映射。對這個規模是真的成本
- id 保留 domain 的 `ArticleId` 而不是 `String`：它不被顯示，它是點下去時交還回來的東西
- `DetailViewModel` 因此要自己留住 `ArticleItem`（收藏要把整篇交給 store，而畫面只拿到字）

**這一則是使用者決定的**，不是我。他指定要 DTO → domain → UI state 這個方向。

## 23. 天氣的新鮮度跟著來源的格線走，不是自己數分鐘

**這一則是使用者決定的。**

**選了** —— 不設固定的 TTL。Open-Meteo 每筆讀數自己帶時間戳與步長：

```json
"current": { "time": "2026-09-01T12:30", "interval": 900, "temperature_2m": 30.4 }
```

`interval: 900` 秒＝**來源每 15 分鐘才產生一個新值**。所以「下次值得問」不是一個要挑的
數字，是**來源自己說的下一格**。拿到 12:30 那一格，就等到下一格出現再問。

**為什麼固定 TTL 是錯的** —— 實測：12:5x 去問，拿回來的是 **12:30 那一格**。
**取得的當下它就已經二十幾分鐘舊了。** 原本 10 分鐘的窗因此保證有 1/3 的請求
必然拿回同一個數字——**那不是新鮮度政策，那是浪費**。

固定 TTL 只能猜「大概多久會有新的」；跟著格線走是**知道**。

**實測到的陷阱** —— 天真的寫法 `下次 = measuredAt + interval` 會壞：
12:52 拿到 12:30 那格，算出 12:45，**那已經是過去**，於是立刻視為過期 → 再問 →
還是 12:30 → 無窮迴圈。原因是來源有發布延遲，格子的值不會在格子的時刻立刻出現。

正確的寫法是**往前推到「現在之後的第一格」**：

```
nextReading = measuredAt + interval × ceil((now - measuredAt) / interval)
```

12:52 拿到 12:30 → 13:00。到 13:00 時 12:45 那格已經發布。**它會自我校正**：
來源落後多少，這個式子就順著落後多少，永遠不會問得比來源產出更快。

**當時還考慮**

- **固定 TTL（原本的做法）。** 一個要辯護的數字，而且必然浪費，見上
- **`Cache-Control` / ETag。** 實測兩個 API 都**沒有 ETag／Last-Modified**，
  條件式請求走不通。SNAPI 有 `max-age=600`，Open-Meteo 什麼都沒有
- **背景定時更新。** 讀者沒在看的時候花他的流量，方向相反

**取捨** —— 多解析一個欄位（`interval`），而且要處理來源沒給 `interval` 的情況
（退回固定窗）。換到的是**一個不需要辯護的數字**：它不是我們選的。

**文章不套用這個。** 它們沒有格線——新聞不是每 15 分鐘產生一批。文章的陳舊只造成
「少了新的幾篇」，不會讓已顯示的內容變成假的（實測：同一篇文章 3.5 小時前存下的
六個顯示欄位與現在的 API 完全相同），所以固定窗對它是合適的形狀。

## 24. 天氣是一條串流，不是一個可以問的問題

**這一則是使用者決定的。**

**選了** —— `WeatherRepository` 對外只有一個 `StateFlow<Weather?>`：

```kotlin
interface WeatherRepository {
    val current: StateFlow<Weather?>
}
```

有人在看 → 它照來源的節奏自己保持最新。沒人在看 → 它保留最後一筆，一個請求都不發。

**為什麼 `suspend fun current()` 是錯的形狀** —— 它把「什麼時候該問」推給每一個呼叫端，
而這件事**外洩了兩次**：第一次是政策算出了「05:30 該再問」卻**沒有任何東西會去問**
（唯一的呼叫端是 `FeedViewModel.init`，而那個 ViewModel 綁在根目的地上，
整個 session 只建構一次）；第二次是我的修法——在 composable 上掛一個
`LifecycleResumeEffect` 去補洞，那是在錯的東西上疊補丁。

**串流讓那個決定回到唯一知道答案的地方**：知道來源多久產生一個新值的，只有來源。

**沒有失敗狀態** —— 型別是 `Weather?` 而不是 `WeatherResult`。因為 `WeatherResult.Failed`
從頭到尾只被用來做一件事：不顯示卡片。讀者是為了文章來的，一張填不出來的卡片，
不見比道歉好。失敗**不清掉手上那一筆**——原因留在 `lastProblem` 供查。

**畫面用 `combine` 接上，不是用欄位帶** —— `FeedViewModel` 原本每一條建出 `Content`
的路徑都要記得 `weather = sky`，而**曾經有一條忘了**，卡片就會依請求回來的先後而消失
（第二個模型抓到的）。現在是：

```kotlin
val state = combine(_state, weather.current) { feed, sky ->
    if (feed is Content) feed.copy(weather = sky?.headline()) else feed
}
```

**結構上不可能再漏掉。**

**取捨** —— repository 需要一個活得比畫面久的 scope（DI 注入一個 application scope）。
這牴觸了 `DECISIONS.md` 21 寫的「沒有 scope 是自己開的」，那一條說的是**畫面的工作**；
一條被所有畫面共用的串流不能隨著其中一個畫面結束。這是官方 data-layer 的標準形狀。

**存的是 `fetchedAt` 不是 `askAgainAt`** —— 存輸入而不是存結論。規則改動時，
已經在磁碟上的讀數會跟著新規則走；存結論的話會卡在舊規則直到被重抓。

## 25. 文章沒有新鮮度視窗，因為沒有人會不小心去要第一頁

> **這一則的結論成立，但它的最後一步走得比這裡寫的更遠**（第 28 則）：下面說「檔案留著，
> 但改了身分」——後來連檔案也沒留。Paging 沒有辦法被交付一段初始資料，一個只在失敗時
> 被讀的快取因此沒有入口，`ArticlesWithAFallback`、`FileArticleCache`、`articles.json`
> 三個一起刪掉（commit `1a2fc6c`）。feed 現在完全沒有磁碟快取。

**這一則是使用者決定的。**

**選了** —— 拿掉文章的 15/60 分鐘視窗、`force` 參數、`Freshness`、`Cadence`、`DataCost`
與 `ACCESS_NETWORK_STATE` 權限。**每一次要第一頁都打網路。**

**理由** —— 會要求第一頁的只有兩種情況：**app 啟動**，以及**讀者下拉**。兩個都是有人
開口說「我要看這個 feed」。往下捲不會要第一頁（那是 `after != null`，走伺服器給的
`next` 連結，快取完全不參與），回到畫面也不會（`cachedIn` 讓串流常駐）。

**所以那個視窗在防的是一件不存在的浪費。**

**視窗最後剩下的唯一工作**是：Android 殺掉程序後讀者切回來——那次冷啟動不是他要求的。
使用者的裁決是「**會被殺掉基本上代表他很久沒用這個 app，回來重抓是合理的**」。

**`force` 因此消失** —— 它存在的唯一目的，是分辨「冷啟動」和「下拉」這兩個共用同一個
函式的入口。沒有視窗要繞過，就沒有東西要分辨。

**檔案留著，但改了身分** —— `articles.json` 不再參與「要不要打網路」，只在**請求失敗時**
被讀。類別因此改名 `CachingArticles` → `ArticlesWithAFallback`，`CachedArticles` 也不再
需要 `fetchedAt`：**沒有人再讀它，就不該存它**。

**freshness 這個必備項還在，只是不再是一組數字**

| | 政策 |
|---|---|
| 天氣 | **來源說的下一格**（`interval: 900`） |
| 文章 | **開 app 抓一次，下拉抓一次，其餘永不** |

兩個都不是憑空挑的數字。「**我們從不發出沒有人要求的請求**」對「不要浪費行動網路」
是比「15 分鐘」更硬的回答。

**代價** —— 系統回收程序後切回來會重抓一次。這是刻意接受的。

## 26. 分頁的重複在客戶端擋，遺漏擋不住——而且不假裝擋得住

**選了** —— 保留伺服器的 offset 分頁，在 `ArticlePagingSource` 裡以「這一代已經給過什麼」
的集合去重。**不做 keyset 游標。**

**先量，再決定**

| 問題 | 實測 |
|---|---|
| 單次回應會給重複的 id 嗎 | **不會。**`limit=20` 和 `limit=100` 都是 id 全相異 |
| 同一個過去的截點，集合會長大嗎 | **會。**`published_at_lte=2026-08-31T00:00:00Z` 一天內 35882 → 35883 |
| 排序有平手嗎 | **有。**100 篇裡兩個時間戳被多篇共用（一個 2 篇、一個 4 篇） |
| 平手的順序穩定嗎 | 連抓三次一致，但排序鍵只有 `published_at` 一欄，**沒有保證** |
| 能不能複合排序 | **不能。**`ordering=-published_at,-id` → `"-id is not one of the available choices"` |

**所以重複不是後端造成的** —— 每一次回應內部都自洽。重複是**我們**把兩次獨立查詢當成
同一份清單的前後段拼起來造成的，而 `offset=N` 的語意是「跳過**現在**這個查詢的前 N 列」，
API 從來沒承諾過可拼接。

**為什麼不做 keyset** —— 標準 keyset 有一個硬性前提：**排序鍵必須唯一**，不唯一時
必須加第二欄位打破平手，否則邊界上時間相同的紀錄會被跳過或重複
（[Stacksync](https://www.stacksync.com/blog/keyset-cursors-postgres-pagination-fast-accurate-scalable)）。
SNAPI 拒絕複合排序，這個前提給不起——**做出來的會是一個在它自己的已知失效點上失效的 keyset**。

**業界對「伺服器不可控」的答案就是客戶端去重**
（[Paging 3 遷移指南](https://developer.android.com/topic/libraries/architecture/paging/v3-migration)
與相關實務文章的一致做法）。

**去重放哪裡** —— `PagingSource` 的 instance。**一代 = 一個 instance**，所以那個集合的
壽命剛好等於「這一代已經給過什麼」：不需要清除、不需要旗標、換代時連同 instance 一起消失。
放在 ViewModel（原本的位置）是錯的，因為 ViewModel 活得比一代久——它現在能work是巧合。

**擋不住的：遺漏** —— 如果是**刪除**而不是插入，邊界往前滑，中間那一篇**沒有人會看到**，
客戶端偵測不到。這無解，而且**不會被寫成解決了**。

**兩個保證的差別**

| | offset + 去重 | 需要什麼 |
|---|---|---|
| 不重複 | ✅ | 客戶端集合 |
| 不遺漏 | ❌ | 真正的 keyset（本 API 給不起） |
| 清單凍結在某一刻 | ❌ | 伺服器發的 snapshot token（本 API 沒有） |

**這一則推翻了 `DECISIONS.md` 16** 的說法。那時寫「釘住視窗大幅降低位移」是推理；
現在有數字，而且知道降低的是哪一種、剩下的是哪一種。

---

## 27. 設計稿的每一個顏色都放進 Material 既有的色票欄位，不另開一組 token

**選了** —— 把參考稿上量到的顏色映射到 `ColorScheme` 現成的欄位（`primaryContainer`、
`secondaryContainer`、`surfaceContainer`、`tertiaryContainer`……），連同 `Shapes` 與
`Typography` 一起定義在 `MosaicTheme`。畫面裡沒有任何一個 `Color(0xFF...)`。

**當時還考慮** —— 開一個 `MosaicColors` data class 加 `CompositionLocal`，把漸層的起訖色、
來源標籤的底色這些 Material 沒有對應語意的顏色收進去。這是擴充 Material 主題的標準做法，
語意也誠實得多。

**為什麼不** —— 要讀得到那個 `CompositionLocal`，就得 import 得到它，也就是
`:feature:saved` 必須相依 `:core:ui`；而第 2 則那張 `allowedProjectDependencies` 只允許
它相依 `:core:domain`。**主題是 Compose 從 composition root 傳下來的，不需要 Gradle 邊；
一組自己的 token 需要。** 為了顏色去鬆綁架構規則，代價不對。

**取捨** —— 有幾個欄位裝的東西和它的名字不完全相符：天氣卡的漸層是
`primaryContainer → secondaryContainer`，來源標籤借用 `tertiaryContainer`。
Material 的語意被挪用了一層，讀 theme 的人得看註解才知道為什麼。換來的是
**模組圖一條邊都沒有新增**，而且連讀不到 `:core:ui` 的 `:feature:saved` 都拿得到整套配色。

**同一個理由造成的重複** —— 離線／錯誤那張小卡在 `:feature:feed` 和 `:feature:saved`
各寫了一份。能共用的前提同樣是那條被禁止的邊。十幾行的重複比一條架構例外便宜。

## 28. 分頁交給 Paging 3，狀態不進 UI State

**這一則是使用者主導的**——包括「刷新是命令、建模成事件」和「別讓 Paging 承擔冷啟動快取」
這兩個把設計拉回正軌的判斷。

**選了** —— `Pager` 建在 ViewModel，清單是它自己的一條 flow：

```kotlin
val stories: Flow<PagingData<ArticleRow>> = reloads.receiveAsFlow()
    .onStart { emit(Unit) }
    .flatMapLatest { newGeneration() }
    .cachedIn(viewModelScope)

fun refresh() { reloads.trySend(Unit) }
```

**`PagingData` 不放進 UI state** —— 官方文件寫明「**每個 `PagingData` 實例預設只能使用一次**」，
而 `data class` 的欄位會被反覆讀取（每次重組、每次 `copy`、每次相等性比較）。它是一個
**進行中的句柄，不是值**。所以 MVI 的狀態與分頁資料是兩條獨立暴露的東西
（[Load and display paged data](https://developer.android.com/topic/libraries/architecture/paging/v3-paged-data)）。

**可以推導的東西不另外儲存** —— Loading／Empty／Ready／Failed 全部由
`feedPhase(loadState, itemCount)` 從 Paging 已經報告的東西算出來。存一份在 state 裡
就是同一個事實的第二個版本，而兩份會不同步。

**刷新是事件，不是狀態** —— `Channel(CONFLATED)`，不是 `StateFlow<Int>` 計數器。
計數器唯一的作用是繞開 `StateFlow` 的去重，那本身就是選錯原語的徵兆。

**`onStart` 不是偽裝的事件** —— 它是「開始收集」這個動作的 hook，而那正是想要一個
世代的兩個理由之一。把種子塞進 channel 語義不同：那是「有一件事排隊等著」，
沒人收集時它會一直躺在緩衝區。

**`initialLoadSize` 必須明寫** —— 預設是 `pageSize × 3`，而來源只在第一次請求認 `limit`，
之後把它抄進每一個連結。放著不管，**每一頁都會變成 60 篇**，花的是別人的流量。

**刪掉的** —— `FeedUiState` 整個（五個型別加五個旗標）、`FeedViewModel` 裡的
`next`／`loading`／`kept`／`beganLoading`／`loaded`／`failed`，以及畫面裡那個
「捲到接近底部就載入」的效果。**那個無鎖的 `loading` 布林特別值得拿掉**——
同一個專案裡 `FileSavedArticles` 為了同樣的危險用了 `Mutex`。

**測試從 24 個變成 4 個**，不是覆蓋變少：哪一個畫面在顯示搬到 `FeedPhaseTest`（5 個），
一個世代會不會重複搬到 `ArticlePagingSourceTest`（6 個）。留在 ViewModel 的是
Paging 不做的事——什麼時候開始工作、字長什麼樣、下拉真正造成什麼。

**裝置上驗過的兩件**：捲到底會載入下一頁；**下拉更新時清單不會閃空白**——`cachedIn`
在新世代載入期間保住已有的項目，所以不需要另外拿一個 `refreshing` 布林。


## 29. 閱讀清單改成一張表，因為當初不選 Room 的那個理由已經被量掉了

**選了** —— `:core:data` 的 `RoomSavedArticles`：一張 `saved_articles` 表、一個
`SavedArticleDao` 的可觀察查詢，外加一次性的 `ImportSavedArticles`——把舊的
`saved-articles.json` 讀進來就把它放掉。**`SavedArticles` 這個介面一個字都沒改**，
`SavedViewModel`、`DetailViewModel` 與它們兩個 `FakeSaved` 一行都沒動。
第 12 則寫過「換掉的成本是這個檔案，不是呼叫端」——這個 commit 是驗證那句話的機會，它成立。

**這一則取代第 12 則的結論，但不改寫它。** 12 則拒絕 Room 只給了一個理由，而且講得很精確：
「Room 的記憶體資料庫需要 Android context，這個專案沒有 Robolectric，所以 DAO 的行為在 JVM 上
測不到；用 Room 等於加一個沒有測試的持久層」。所以這裡要說的**不是「Room 後來比較好」，
而是「當初讓它不對的那件事現在不成立了」**——Robolectric 進了 `:core:data` 的 test classpath，
18 個資料庫層的測試在純 JVM 上跑得起來。12 則原文保留，history 是交付物。

**沒有做的：遠端資料來源。** 官方 offline-first 的形狀是「本機 ＋ 遠端 ＋ 一個決定用哪個的
repository」，那個形狀存在是為了**調解**：遠端是權威、本機是它的快取，兩邊會不一致。
這裡三個前提都不成立——SNAPI 沒有帳號、沒有認證、沒有「這個讀者存了什麼」這種端點。
所以本機不是快取，它就是**紀錄本身**，沒有東西要調解、沒有衝突規則要挑、沒有版本要追。
`RemoteSavedArticles` 只會有一個誠實的實作（什麼都不回、什麼都不收），
而 repository 那個 `when` 的另一條分支存在的唯一功能是讓一張架構圖成立。

唯一不算蠢的遠端工作是「線上時把存下來的那份重抓一次」，它同樣不需要：第 23 則已經量過
同一篇文章 3.5 小時後六個顯示欄位完全相同；而且 `DetailViewModel` 本來就先問
`articles.article(id)`、失敗才退回存下來的那份——**存下來的那份只在網路答不出來的時候被顯示**，
那正好是重抓不可能發生的那一刻。

**三件量出來的事（不是推論）**

| 問題 | 實測 |
|---|---|
| Room 的 `Flow` 在 Robolectric 下會不會重發 | **會。**但必須用 `setQueryCoroutineContext` 把 `runTest` 的 scheduler 交給 Room——用 Room 自己的 executor 時重發走真的背景執行緒，`runTest` 的虛擬時鐘不會等它 |
| 那個 dispatcher 可不可以是 `UnconfinedTestDispatcher` | **不行。**Room 會對它呼叫 `limitedParallelism`，那個型別回 `UnsupportedOperationException`。必須是 `StandardTestDispatcher` |
| 寫入失敗丟什麼 | 關掉資料庫丟的是 `JobCancellationException`——它是 `CancellationException`，依第 21 則必須重丟，**所以「關掉資料庫」根本觸發不到那個分支**。真正的 SQL 失敗丟 `android.database.sqlite.SQLiteException`，不是 `androidx.sqlite` 的那個 |

第三列正是第 18 則那個錯誤的形狀，所以這次的順序是：先寫測試、讀 stack trace、再寫 catch。

**不會再發生的事**（不是承諾，是理由）——寫到一半的檔案（SQLite 的寫入路徑是交易式的，
`.writing` 加 rename 那套沒了）；一個壞位元組賠掉整份清單（文件要整份能解析，表不用）；
讀不回來的時間戳（`published_at` 存 epoch 毫秒，而 `Instant.ofEpochMilli` 在 `Long` 上是全函數，
所以第 18 則那個分支不是「不太可能」，是**到不了**，該刪而不是留著當裝飾）；
以及建構子裡那次同步讀檔（Room 的 `Flow` 是冷的，沒人收就不碰磁碟）。
**還有那面手動維護的鏡子**：檔案版有一條「任何成功的寫入都要順便指派 `articles.value`」的規則，
它成立是因為有人記得。第 24 則記的正是有人忘記的後果。現在那條規則不存在了。

**`saved_at` 是檔案不需要的那一欄** —— JSON 陣列有順序，SQL 的表是集合，而
`SavedArticles` 的 KDoc 寫著「最近存的在最前面」，所以順序必須變成資料。
`ORDER BY saved_at DESC, id DESC`：兩次點擊不會落在同一毫秒，但測試迴圈裡的三次會，
而順序取決於 SQLite 未定義的平手規則的測試，會在別人的機器上壞掉。

**當時還考慮**

- **`@Upsert`**（Room 2.5+，UPDATE-then-INSERT，保留 rowid、不觸發 delete trigger）。
  這裡沒有 trigger、沒有外鍵、沒有人引用 rowid，所以它什麼都沒買到，而 `INSERT OR REPLACE`
  才是測試名字在描述的那件事。值得寫下來的是：**第二張表指過來的那天這個選擇就重要了**——
  `REPLACE` 會沿著外鍵串連刪除。
- **自動遞增的 `Long` 主鍵。** `ArticleId` 已經是整個 app 的文章身分（第 5 則），
  每個呼叫端都拿它當 key；合成主鍵等於多一個身分，再加一次翻譯。
- **`.fallbackToDestructiveMigration()`。** 版本 1 沒有東西可以退回，現在加等於預先授權
  某次版本升級時安靜刪掉讀者的清單——那正是第 12 則花一段在反對的事。
- **叫 `MosaicDatabase` 或 `AppDatabase`。** 第 14 則的同一條理由。真到了加第二張表那天，
  那是 `@Database(entities = [...])` 一行加一次 migration，只有名字讀起來怪；
  **名字晚點讀起來怪，比名字現在讀起來模糊便宜。**
- **把舊檔案直接丟掉。** app 沒上架過，整個安裝基數就是開發者自己的測試機，三下就能重建。
  但這是整個變更裡**唯一會掉資料**的地方，而第 12 則反對的正是這種安靜的損失。
  何況便宜的那個選項跟不會掉東西的那個選項是同一個：一次 `readText`、一次 `mapNotNull`、
  一個 `INSERT`、一次 `delete`。成功刪檔、解不開改名成 `.unreadable`——
  **成功時多一份是雜訊，失敗時那些位元組是唯一的證據。**

**代價**

- 多一個相依，測試變慢（Robolectric 每個 test class 開一次 sandbox）
- `saved_at` 是檔案不需要的一欄
- `published_at` 存 epoch 毫秒，比毫秒細的精度會被截掉。repo 裡每一個 fixture 都是秒精度，
  但「SNAPI 永遠不送更細的」**沒有被驗證過**
- **磁碟滿與真正的資料庫毀損現在測不到。** 檔案版還能靠占住那個路徑假造第一種，SQLite 沒有
  對應的縫。寫進 README 的延後表，而不是假裝它被涵蓋了
- 這個功能大概只用到 Room 的五分之一。**為了「用資料庫」不值得換；為了那個可觀察的查詢
  與交易式的寫入才值得**

---

## 30. 一篇文章的來源由 repository 決定，而存過的那份就是它

**選了** —— `article(id)` 先問 `saved_articles` 裡的那一列，沒有那一列才去問網路。
`DetailViewModel` 只呼叫 `articles.article(id)`，`keptCopyOf` 整段刪掉。
`NetworkArticleRepository` 因此改名 `SavedFirstArticleRepository`——它已經不只有網路一個來源，
名字再叫 Network 就是在講一件不成立的事。

**為什麼是 repository 而不是 ViewModel** —— 官方資料層文件把這件事明列在 repository 的職責裡：

> "Repository classes are responsible for ... Resolving conflicts between multiple data sources"

同一頁還寫著

> "each repository defines a single source of truth"

（<https://developer.android.com/topic/architecture/data-layer>）

改之前，「一篇文章」沒有單一真相：網路是 `ArticleRepository` 的真相、Room 是 `SavedArticles`
的真相，而 `DetailViewModel` 夾在中間挑。挑的規則寫在畫面裡，所以第二個要顯示一篇文章的地方
（深連結、widget、之後的 saved 詳情）都得把同一條規則再抄一次——**抄錯了不會有人發現**，
因為沒有任何一個測試的主詞是「這條規則」。

**順便修掉的**：舊寫法是 `kept.saved.first().firstOrNull { it.id == id }`——為了看一列而把
整張表讀進記憶體，再用 Kotlin 掃一遍。這張表本來沒有 `WHERE id = ?` 這個查詢，現在有了
（`SavedArticleDao.find`）。這本來就是 SQL 該回答的問題。

**行為改了，而且是刻意的** —— 第 29 則寫過「存下來的那份只在網路答不出來的時候被顯示」。
現在反過來：**存過的那篇根本不再問網路**。SNAPI 的 `/articles/{id}` 回的就是摘要、沒有全文，
所以重抓買到的只是六個顯示欄位有沒有變，代價是一次請求加一個轉圈——而且付在讀者唯一
講明「我要它離線也在」的那篇文章上。第 23 則量過同一篇文章 3.5 小時後那六個欄位完全相同。

**代價（誠實的那一半）** —— **存過的文章不再跟著來源更新**。標題改了、摘要修了、圖換了，
讀者看到的還是他按下儲存那一刻的那份，直到他取消儲存再開一次。這是 offline-first 一定要付的錢；
差別在於這裡沒有「線上時在背景刷新」那一層把它補回來，而**不做那層是選擇不是遺漏**：
要做就得有 conflict policy（誰贏、`saved_at` 要不要跟著動、清單順序會不會因此跳動），
那是為了一組量過沒有變化的欄位付的複雜度。真的需要那天，加的地方在 repository 這一層，
`DetailViewModel` 不會知道有這回事——這正是把仲裁搬下來換到的東西。

**`articles(after:)` 一個字都沒改** —— feed 沒有變成 offline-first，也不打算（第 25、28 則）。
所以名字不叫 `OfflineFirstArticleRepository`——那是 Now in Android 的慣例，但它會宣稱一件
這個類別只做了一半的事。`SavedFirstArticleRepository` 說的剛好是它做的：**存過的那份優先**，
而 feed 從來沒有存過的那份，所以 feed 那半自然退化成只有網路。名字承認這個分裂，KDoc 講為什麼。

**當時還考慮**

- **只把 `kept.saved.first()` 換成 `find(id)`，仲裁留在 ViewModel。** 少讀一整張表，
  但單一真相還是沒有，規則還是抄在畫面裡。省下的是這件事最不重要的那一半。
- **在 `:core:domain` 加一個只有 `find` 的介面讓 repository 依賴。** domain 已經有
  `SavedArticles`，再加一個等於同一張表開兩個門。`:core:data` 內部直接依賴 DAO 是
  Now in Android 的做法（`OfflineFirstNewsRepository` 拿的就是 `NewsResourceDao`），
  而且 module 邊界沒有變寬——DAO 仍然 `internal`，跨出 `:core:data` 的還是只有 domain 的介面。
- **讓 `article(id)` 回報這一份是從哪裡來的**（`Loaded(article, fromDisk = true)` 之類）。
  沒有呼叫端要用：`DetailUiState.Content.saved` 問的是「現在還存著嗎」，不是「這份從哪來」，
  而那兩個問題會在讀者按下取消儲存的那一刻分岔——畫面要的是前者。
- **保留 ViewModel 那條退路當第二層保險。** 它已經到不了（存過的那篇 repository 就回了），
  留著是一段測試覆蓋不到的死碼，而且會讓下一個讀 `DetailViewModel` 的人以為仲裁還在這裡。
- **讀不回來的那一列就讓它炸。** `SavedArticleEntity.toArticle()` 對域模型不接受的值會丟
  `IllegalArgumentException`，而這個 class 的 KDoc 第一句是「失敗在這裡停止被丟出、變成答案」。
  所以那一列被當成「這裡沒有」處理、掉回網路——第 29 則的 `readable()` 用同一個理由
  少算一篇而不是少算一整份清單。

**一個測試往下搬了一層** —— `DetailViewModelTest` 的
`an article that was kept opens with no network at all` 斷言的行為沒有消失，
但它不再是畫面的事。同一句話現在由 `OneArticleTest` 斷言，而且更強：不是「網路失敗時
退回存下來的那份」，是**一個請求都沒有發出去**。`DetailViewModelTest` 原來的位置換成反向的
那一條——repository 說失敗，畫面就說失敗，即使那篇存過——它守的正是「不要把仲裁搬回來」。

---

## 31. 導覽自成一個模組，`:app` 只剩下組裝

**選了** —— 新增 `:navigation`：三個 `NavKey`、`NavDisplay` 的 `entryProvider`、
back stack 的兩個操作（`goTo`／`goToDestination`），以及上下兩條 bar 的 chrome，
整批從 `MainActivity.kt` 搬過去，對外只留一個 `Mosaic()`。`:app` 剩下 `MosaicApp`、
`MainActivity` 與套一次主題——**它現在是 composition root，僅此而已**。

**為什麼** —— `:app` 原本同時做兩件事：Hilt 的 composition root，和唯一知道
「哪個畫面通往哪個畫面」的地方。兩件事都在「最上面」，而那是它們唯一的共同點：
前者存在是因為 Android 需要一個 `Application`，後者存在是因為畫面之間得有人接線。
拆開之後，第 2 則那張表才說得出一句它原本說不出口的話——**`:app` 不得相依任何
`:feature:*`**。原本那條「`:app` 看得到所有人」允許的東西太多：畫面決定重新長回
`MainActivity` 的那天，檢查不會有任何反應。

**驗證過新規則會失敗** —— 暫時讓 `:app` 相依 `:feature:feed`，檢查如預期報出
`:app must not depend on :feature:feed`，然後還原。第 2 則對檢查的標準在這一條上一樣適用。

**feature 那三條邊用 `implementation`** —— 一開始寫成 `api`，理由是讓 feature 留在 `:app`
的 compile classpath 上，因為 Hilt 的元件在 `:app` 產生、要點名三個 `@HiltViewModel`。
實測 `implementation` 也產得出完整的元件（Hilt Gradle plugin 的 aggregating task 讀的是
runtime classpath），而三個畫面在 `implementation` 下也在模擬器上逐一開過、書籤存取正常。

**改回來的理由是 `api` 會把這整條規則變成一句空話**：`api` 是傳遞性的，`:app` 因此
仍然寫得出 `import moozy.mosaic.feature.feed.FeedScreen`，而 `checkModuleDependencies`
只讀宣告出來的 `ProjectDependency`，看不到這件事。那條規則會變成「檢查 `:app` 有沒有
把話說出口」，而不是「檢查 `:app` 碰不碰得到」。獨立審查的第二個模型也指向同一點。

**取捨** —— **耦合沒有消失，它搬到了一個名字說得出它是什麼的模組裡。**`:navigation`
依然看得到三個 feature，這無法迴避：接線的人必須認識被接的兩端。換到的是那份知識
有了自己的名字與自己的邊界，不再混在「Android 進入點」裡。

**而且這條規則比它看起來弱一格** —— `api` 是傳遞性的，`:feature:*` 的型別因此仍然出現在
`:app` 的 compile classpath 上：`:app` 真的寫下 `import moozy.mosaic.feature.feed.FeedScreen`
是編得過的，而 `checkModuleDependencies` 只讀**宣告出來的** `ProjectDependency`，看不到這件事。
它擋的是「`:app` 宣告一條 feature 的邊」，不是「`:app` 碰得到 feature 的型別」。
差別要講清楚，不要讓讀者以為它擋得比實際多。

**當時還考慮**

- **留在 `:app`。** 零成本，三個畫面的圖也小到一個檔案裝得下。放棄的原因不是檔案太大，
  是那條 Gradle 規則因此永遠只能寫成「`:app` 看得到所有人」——第 2 則的整個價值在於
  規則要能失敗，而一條允許一切的規則失敗不了。
- **Now in Android 每個 feature 拆 `api` 與 `impl`。** NiA 需要那一刀，是因為它的 feature
  透過一個共用的 `Navigator` 自己導覽，所以 feature 必須看得到彼此的 key。這個專案不是
  那樣接的——畫面只收 callback，key 一個都不往下傳，所以既不需要 api/impl，也不需要
  一個共用的導覽介面。
- **順手讓 `:navigation` 也把主題套起來。** `:app` 會更薄，但主題是「這個 app 長什麼樣」，
  不是「哪個畫面通往哪個」。合在一起只是把剛拆開的東西換個地方黏回去。

**代價** —— 多一個模組就是多一份建置設定與一個編譯單元；`:app` 的 project 相依從六條
變成四條，數量上省得不多。省下的不是邊的數量，是**`:app` 能做的事的種類**。

---

## 32. 轉場的兩個 scope 走 `:core:ui` 的 CompositionLocal，feature 不認識 Navigation 3

**選了** —— `:core:ui` 開一個預設為 `null` 的 CompositionLocal，裝著
`SharedTransitionScope` 與 `AnimatedVisibilityScope`，外加三個用這個 app 的話命名的
modifier：`sharedArticleCard`、`sharedArticleImage`、`sharedArticleTitle`。
`:navigation` 把 `NavDisplay` 包進 `SharedTransitionLayout`，在每個 `NavEntry` 裡讀
`LocalNavAnimatedContentScope`，把兩個 scope 填進去。三個 feature 只呼叫 modifier，
`androidx.navigation3.*` 一行都沒有。

**為什麼不讓 feature 直接相依 Navigation 3** —— 第 31 則剛把「哪個畫面通往哪個」
收進 `:navigation`。`LocalNavAnimatedContentScope` 是 Navigation 3 的型別，feature 一旦
import 它，就等於重新知道自己是被誰導覽的——那條邊界會在同一天失效。要注意的是
**這一條沒有機器守著**：`checkModuleDependencies` 讀的是 project 相依，看不到函式庫相依，
它靠的是 `:feature:*` 的 `build.gradle.kts` 裡根本沒有 navigation3 這個座標。

這條路走得通，是因為 **`SharedTransitionScope` 與 `AnimatedVisibilityScope` 是 Compose 的
型別，不是 Navigation 3 的型別**——Navigation 3 只是剛好持有它們。主題早就是這樣過去的
（`MosaicTheme` 套一次，底下每個畫面讀 `MaterialTheme.colorScheme`，中間沒有 Gradle 的邊），
轉場 scope 走同一條路。

**降級要安全** —— 沒有人提供時 local 是 `null`，三個 modifier 原樣回傳 `this`。
`@Preview` 與測試沒有轉場可以參加，而一個只有在動的時候才畫得出來的畫面，
是沒有人看得到的畫面。

**Reading 與 Saved 是兄弟，不是父子** —— 底部那條 bar 原本一邊是 push（`add(SavedKey)`）、
一邊是 pop（`while (size > 1) removeAt(...)`），而 Navigation 3 對這兩件事的動畫不同，
所以同一次切換的兩個方向長得像兩種手勢：一個往前推進，一個往回縮掉。**錯的是動畫，
不是堆疊**——Reading 必須留在堆疊底部（讀者按 Reading 再按返回，不該掉進一個他沒選過的
Saved），但「誰在誰下面」這件事對「切換長什麼樣」沒有發言權，因為這兩個目的地誰也不包含誰。
所以 back stack 一行沒動，只有 `SavedKey` 多了三段 metadata，兩個方向是同一個橫向平移的鏡像。

**key 用型別，而且帶著來源清單** —— 用 data class 不用字串（Compose 文件明說），因為
兩個模組要湊出同一把 key，而 `"article-image-$id"` 是拼字上的默契，編譯器沒有意見。
帶 id 是因為讀者可以在前一篇還沒退場時開下一篇。帶 `CardOrigin` 則是設計時才想通的：
橫向切換的那半秒 Reading 與 Saved 同時在畫面上，而一篇從 feed 存起來的文章兩邊都有——
共用一把 key 會讓那張卡片自己跟自己配對，在別人平移的時候斜著飛過去。
`CardOrigin` 由 `ArticleKey(id, from)` 帶著，`:navigation` 填進 local，feature 看不到它。

**當時還考慮**

- **feature 直接 import `LocalNavAnimatedContentScope`。** 少一個 CompositionLocal、
  少兩條模組邊。放棄的理由見上：那正是第 31 則拆掉的那條線。
- **兩個 scope 當參數一路傳下去。** 不需要 CompositionLocal，但每個畫面、每個列表項的
  簽章都要多兩個只跟動畫有關的參數，而中間經手的 composable 一個都用不到。
- **只給 feed 做容器轉場，Saved 不做。** 那樣就不需要 `CardOrigin`。但兩個清單通往
  同一個畫面，只有一邊會動比兩邊都不動更像壞掉。
- **來源放在 back stack 以外的地方**（例如 `Mosaic()` 裡一個 `rememberSaveable`）。
  能動，但那是一份跟 back stack 平行、必須手動保持同步的狀態，而 back stack 本來就是
  「讀者從哪裡來」的唯一紀錄。
- **標題也用 `sharedElement`。** 兩邊是同一串字，但卡片給它兩三行、文章給它全部，
  `sharedElement` 會在飛行中重排文字。改用 `sharedBounds` 加 `scaleToBounds()`——
  它量一次最終版面再縮放，這也是 Compose 文件對文字的建議。圖片兩邊是同一張照片，
  所以維持 `sharedElement`。
- **文章畫面也給一段滑動。** 卡片的邊界已經在長大，再加一段位移就是兩個動作在描述
  同一段路程，而且對讀者的視線落點各說各話。所以文章的進出只有淡入淡出。

**取捨** —— **多了一個 CompositionLocal，就是多了一個隱性相依。** Slack 的 compose-lint
對這件事有話說（`ComposeCompositionLocalUsage`，warning，故意不消音），而它說得對：
讀 `sharedArticleImage(id)` 的人看不出上面必須有人提供過東西。換到的是三個 feature
不必認識導覽函式庫——這筆交易只有在那條邊界值得守的時候划算，而第 31 則已經花過力氣守它了。

**綠燈沒有證明任何一個轉場是對的** —— 這批改動 `build detekt lint` 全綠，但這個專案
沒有截圖測試、也沒有動畫的自動化驗證（第 20 則：畫面本來就沒有測試）。轉場唯一的
驗收方式是在裝置上看，這一條要講清楚，不要讓讀者以為建置通過等於它動起來是對的。

---

## 33. 容器轉場只讓一層淡，而尺寸 modifier 一律放在 shared modifier 之後

**症狀** —— 卡片沒有長大成文章。第 32 則把兩個 scope 接好了，三個 modifier 也都掛上去了，
在裝置上就是看不到那段成長。以下兩處都改，因為兩處都是錯的。

### 一、配對的兩端 modifier 順序要一致，而且尺寸在後

Compose 文件對配對的兩端只有一句話：*"Be consistent with the order of modifiers on the
matching items. Place size modifiers after the shared element modifiers"*。原本 feed 與
Saved 的卡片是 `fillMaxWidth().sharedArticleCard(id)`（尺寸在前），文章畫面是
`sharedArticleCard(id)` 之後才 `fillMaxSize()`（尺寸在後）——同一個矩形的兩端，一端在量
之前就被定死寬度，另一端在量之後才決定。文件說這會造成 *"unexpected visual jumps"*。
三處卡片（feed 的頭條與列、Saved 的卡片）都改成尺寸在後，跟文章畫面對齊。

> **方向跟直覺相反，值得記下來。** 很容易假設「尺寸要放在 shared modifier 之前」，
> 照那個假設改會把已經對的那一端也弄壞。文件的理由在同一段：**前面的 modifier 決定
> 要飛的那個矩形，後面的 modifier 量矩形裡面裝什麼。**

### 二、螢幕層級只留一層淡

`ArticleKey` 原本三個 transition key 都是 `fadeIn() togetherWith fadeOut()`：整個畫面
對淡，蓋在正在長大的邊界上。容器轉場本來就同時有三件事在動（矩形長大、卡片內容退場、
文章內容進場），再加一層全螢幕對淡就是四件事在描述一次點擊，而讀者該跟著看的那一件
是其中最安靜的。

改成**只有文章那一層會淡**：進去時文章的外框（背景與頂欄，這兩樣不屬於任何一個容器）
淡入，清單原地不動；回來時外框淡走，清單本來就在那裡。兩份*內容*之間的交叉淡入淡出
交給 `sharedArticleCard` 自己的 `enter`／`exit`——在正在飛的矩形裡面，那正是容器轉場
把它放的位置。

回程多給一個 `targetContentZIndex = -1f`：`AnimatedContent` 預設把要進場的那一頁畫在
上面，而回程要進場的是清單。清單以全不透明蓋在文章上，會把文章的淡出遮掉，而不是被
它讓出來。

**當時還考慮**

- **文章進出完全不動（兩個方向都用 `EnterTransition.None`）。** 最純粹的容器轉場。
  但 `Screen` 的 `Scaffold` 是不透明的 `background`，外框會硬切進來——變成一次硬切
  接一段成長，比原本更糟。
- **只改順序，不動 transition。** 順序是文件寫死的規則，改它風險最低。但「兩層全螢幕
  對淡蓋在邊界動畫上」是回報症狀最直接的解釋，只做一半等於留著一個已知會蓋住主角的東西。
- **`sharedBounds` 的 `enter`／`exit` 沿用預設不寫。** 它們本來就是 `fadeIn()`／`fadeOut()`，
  寫出來一行行為都不變。還是寫了：螢幕層級的對淡拿掉之後，整個轉場只剩這一處在做交叉
  淡入淡出，讓它在原地讀得出來，比省下兩個具名參數重要。
- **圖片與標題的順序也一起改。** 沒改。它們兩端本來就一致（都是尺寸在前），而文件的
  規則第一句是「一致」；動一個沒壞的配對，只會多一個沒人驗證過的變數。

**取捨與限制** —— **這一則一樣沒有任何機器驗證。** `build detekt lint` 全綠證明它會編譯，
不證明它動起來是對的（第 20 則與第 32 則末段講的是同一件事）。文件說的兩件事——順序
要一致、尺寸放在後面——是查得到的；「一層淡比兩層淡好看」是設計判斷，只能在裝置上看。
下一個人如果在裝置上看到回程變成硬切，第一個該懷疑的就是那個 `targetContentZIndex`。

---

## 34. 文章畫面自己畫邊界，兩個目的地維持原本的外框

**選了** —— `:navigation` 多一個 `EdgeToEdgeScreen`：沒有頂欄、沒有底欄、不從內容扣掉任何
inset，只留一層不透明背景。只有 `ArticleKey` 用它；Reading 與 Saved 仍舊用 `Screen`，一個
pixel 都沒動。`Screen` 的 `onBack` 參數連同頂欄裡的返回箭頭一起刪掉——文章是它唯一的呼叫者，
留著就是一段沒有人走的路。文章的圖片因此貼到最上緣、畫進狀態列底下，返回箭頭浮在圖片上，
位置取自 `Modifier.windowInsetsPadding(WindowInsets.statusBars)` 而不是一個 dp 數字。

**為什麼 scrim 與箭頭要在同一個模組** —— 照片的亮度沒有人能事先知道，所以白箭頭與黑箭頭
都可能看不見；官方 edge-to-edge 指南對這件事的答案是 scrim，而不是選一個 icon 顏色。但
「後面到底有沒有照片」只有畫面自己知道：`ArticleView.imageUrl` 可以是 null，Loading 與
Failed 兩個狀態根本沒有圖。箭頭若留在 `:navigation`、scrim 留在 `:feature:detail`，就會有
兩個必須對齊的東西分在兩個模組，而它們對齊的依據（狀態）只有其中一邊看得到。所以兩個
一起搬進 `:feature:detail`，由 `DetailScreen` 疊在三個狀態之上。

**一道漸層，不是兩道** —— 狀態列圖示與箭頭在同一條垂直帶上、上下相疊。兩道 scrim 疊在
重疊處會把 alpha 加起來，而較短那道的下緣會留下一條看得見的階梯。所以只有一道：從狀態列
上方開始，到箭頭下方才收到全透明（`Reach`＝觸控目標 48dp 再加一段，讓漸層在箭頭「之下」
而不是「齊平」收尾；齊平的話箭頭下半部後面等於沒有東西）。

**顏色取 `colorScheme.scrim`，不取 `surfaceContainer`** —— 指南給的範例用 surface 色，但那是
為了保護「背景已知」的圖示。照片之上，淺色主題的 surface 會在照片上蓋一層白紗，而白色圖示
還是一樣看不見。`scrim` 是唯一一個在深淺兩套配色都是黑的欄位——第 27 則要求每個顏色都落在
既有色票欄位裡，而這一格正是為此存在的。

**沒有圖片的那些情況** —— `overPicture` 為 false 時整道 scrim 不畫，箭頭改用 `onSurface`：
背景是 app 自己的顏色、已知、對比夠，蓋一層黑紗是在保護一個沒有威脅的東西。Content 但沒有
圖片時，捲動內容最上面留一段 `statusBars + Reach` 的空白，讓第一行字落在箭頭下面而不是箭頭
底下；Loading 與 Failed 則整塊 `windowInsetsPadding(systemBars)`——上面已經沒有頂欄替它們
扣掉系統列了。

**inset 只付一次** —— `EdgeToEdgeScreen` 不扣也不 consume，所以每一段 inset 都由用得到它的
那一段自己付：圖片一分不付（它要的就是那塊），箭頭付 statusBars，捲動內容的下緣付
navigationBars，而且付在 `verticalScroll` 之內。官方指南要的正是把 inset 交給 scrollable 的
contentPadding 而不是 padding 它的父層：padding 父層會讓圖片被切掉頂端，也會讓底部那段淨空
變成永遠杵在那裡的邊界，而不是跟著最後一顆按鈕捲走。

**當時還考慮**

- **`Screen` 加一個旗標（或讓 `title` 為 null 就不畫頂欄）。** 改動最小，但 `Screen` 會變成
  一個「有時候是外框、有時候不是」的東西，而兩種模式對 inset 的處理正好相反。兩個名字比
  一個帶旗標的名字誠實。
- **文章仍用 `Scaffold`，只把 `topBar` 留空、`contentWindowInsets` 設 0。** Scaffold 的工作
  就是量 chrome 再從內容扣掉；沒有 chrome 可扣的 Scaffold 只剩一個容器色，而它的
  `innerPadding` 會變成一個永遠是零、卻誘人拿去用的參數。
- **箭頭留在 `:navigation`，只把 scrim 放進 `:feature:detail`。** 見上：兩個必須對齊的東西
  分在兩個模組，而對齊的依據只有一邊看得到。
- **箭頭底下放一個圓形 scrim（相片 app 常見的做法），不做漸層。** 它在任何背景上都成立，
  但保護不到狀態列的圖示，所以狀態列還是得有第二道——又回到「兩道 scrim」那個問題。
- **圖片在文章這端改成 `RectangleShape`，讓它真的方角貼邊。** 沒做。shared element 兩端必須
  同形（`:core:ui` 的 KDoc 與第 33 則都寫了 Compose 沒有形狀動畫），改這一端就得同時改卡片
  那端，而 feed 與 Saved 的卡片不能動。

**取捨與限制**

- **`:core:ui` 一行都沒改。** `sharedArticleImage(id, PictureShape)` 與
  `sharedArticleCard(id, CardShape)` 兩個呼叫點都留在原處，變的只是圖片的目標矩形——現在是
  整個螢幕寬、貼在最上緣。容器仍然 `clip(CardShape)`，所以**文章的四角本來就是 20dp 圓角**；
  圖片頂到最上緣之後，那兩個上角會第一次被看見。這是既有行為被新版面照出來，不是新加的。
  真要方角，要動的是容器的形狀，而那會連著轉場一起改。
- **一樣沒有任何機器驗證。** `build detekt lint` 全綠只證明它會編譯（第 20、32、33 則講的是
  同一件事）。這個專案沒有截圖測試：scrim 的 alpha 夠不夠、箭頭在有瀏海的機器上落在哪裡、
  容器轉場在圖片改成貼頂之後看起來還對不對，全部只能在裝置上看。
- **系統列圖示的顏色沒有動。** `MainActivity` 用的是 `ComponentActivity` 版的
  `enableEdgeToEdge()`，官方文件明講在它之下不要自己設 `isAppearanceLightStatusBars`。
  scrim 存在的理由正是這個：圖示顏色跟著主題走，照片不跟著主題走，中間那段差距只能靠
  scrim 補。

---

## 35. 貼到螢幕邊的那一端不留圓角，圓角在飛行途中自己收掉

> **這一則取代第 34 則裡「shared element 兩端必須同形」那個結論，但不改寫它。**
> 34 則把「圖片在文章這端改成方角」列在「當時還考慮、沒做」，理由是
> 「shared element 兩端必須同形（Compose 沒有形狀動畫）」。那句話**太強**了：
> Compose 文件寫的是**沒有自動的**形狀動畫（*"there is no automatic animation between
> shapes"*），不是不能做。34 則原文保留，history 是交付物。
> 附帶更正一個引用：那句括號說「第 33 則也寫了」——第 33 則沒有寫過這件事，
> 這個說法只存在於 `:core:ui` 的 KDoc 裡。

**選了** —— `sharedArticleCard` / `sharedArticleImage` 不再收一個 `Shape`，改收一個
`ArticleEnd`：`IN_A_LIST` 或 `FILLING_THE_DISPLAY`。兩個半徑（卡片 20dp、圖片 14dp，
以及貼邊的 0dp）留在 `:core:ui` 一個地方，呼叫端只說**自己是哪一端**。
文章那一端因此是方角——**貼著顯示器邊緣的東西不該有圓角**，圓角是要從某個背景上切出來的，
而那裡沒有背景了；feed 與 Saved 的卡片一格都沒動。

**圓角是轉場的函式，不是常數** —— `OverlayClip` 是一個**介面**，它的 `getClipPath` 每一幀
都拿到當下的動畫 bounds（`SharedContentNode` 的 `draw()` 裡呼叫）。所以半徑可以是
「轉場走到哪裡」的函式。這裡用 `AnimatedVisibilityScope.transition` 上的 `animateDp`：
`Visible` 是自己這一端的半徑，`PreEnter` 與 `PostExit` 是**另一端**的。overlay 之外的那層
clip 讀同一個 `State<Dp>`——文件說 overlay 裡的裁切要另外宣告，但沒說那兩份可以不一致，
一個動一個不動就是在打架。

**兩端都要動，不是只有文章那一端** —— `sharedElement` 只畫**正在進場**的那一端。
去程進場的是文章，回程進場的是卡片。如果卡片那端固定 14dp，回程的第一幀就是一張
「整個螢幕寬、貼在最上緣、四角 14dp」的圖——正好是這次要修的那個缺陷反過來。
兩端都用同一條規則（`Visible` = 自己、其餘 = 對面），而它們是同一個 `AnimatedContent`
transition 的兩個子節點，所以逐幀算出同一個數字，誰也不必知道對面是誰。

**當時還考慮**

- **從 bounds 的寬度推半徑**（`OverlayClip` 也允許，而且完全不需要狀態）。**沒有選。**
  寬度在這個 app 裡分不開兩端：頭條卡片的圖片本來就是「螢幕寬減掉列表的 16dp padding」，
  所以照寬度推，要嘛讓半徑在最後那幾 dp 內從滿收到零（一個穿著動畫外衣的硬切），
  要嘛讓頭條那張圖靜止時就幾乎是方角——而 feed 不准動。轉場的進度是真的從一端跑到另一端的
  那個量，寬度只是「差不多」。
- **只改文章那一端，卡片維持常數。** 見上：回程會壞。
- **維持 34 則的結論，改容器的形狀就好。** 不夠。容器（`sharedArticleCard`）的 `clip` 是圖片的
  祖先，20dp 的容器會把圖片的上緣兩角一起磨掉——所以就算圖片改成 0dp，不動容器也看不出來。
  兩個都改，而且用同一條規則。
- **`CardShape` 與 `PictureShape` 兩個常數留著。** `PictureShape` 拿掉了：它存在的理由是
  「逼兩端交出同一個形狀」，而那個理由沒有了，改成一個 `:core:ui` 內部的 `Dp`。
  `CardShape` 留著，但理由換了一個——feed 的 `Surface` 需要一個靜止時的形狀，
  而它必須跟 `IN_A_LIST` 那一端裁切出來的形狀一致。

**取捨與限制**

- **一樣沒有任何機器驗證。** `build detekt lint` 全綠只證明它會編譯（第 20、32、33、34 則
  講的是同一件事）。這個專案沒有截圖測試：圓角收掉的速度跟矩形長大的速度合不合、
  箭頭淡入的長度對不對、回程有沒有真的不再閃一下圓角，全部只能在裝置上看。
- **兩條動畫用的是兩個 spring，不是同一條曲線。** 邊界用 `SharedTransitionDefaults` 的
  `BoundsTransform`，半徑用 `animateDp` 的預設 spring。它們同時開始、都不會過衝到看得出來，
  但沒有人保證它們同一幀結束。真要對齊，得把 `transitionSpec` 也接出來——在裝置上看得出
  差別之前，那是一個沒有依據的參數。
- **feed 的 `Surface` 仍然自己畫 20dp。** 去程時那張卡片是退場的一端，它的背景色由 `Surface`
  自己畫，所以它的圓角在飛行途中不跟著收——它正在淡出，而且 `sharedElement` 的圖片本來就
  只畫進場那端。要讓它也跟著收，`Surface` 的 `shape` 也得變成動畫值，那會把 feed 拉進這次
  改動裡，而 feed 不准動。

---

## 36. 返回箭頭跟著文章一起到，scrim 跟它同進同出

**症狀** —— `WayBack` 是 `DetailScreen` 那個 `Box` 裡的兄弟節點：它在兩個畫面之間
沒有對應物，所以不是任何 shared element，也沒有人給它 enter 與 exit。

**選了** —— `:core:ui` 多一個 `Modifier.appearsWithTheArticle()`，裡面就是
`AnimatedVisibilityScope.animateEnterExit`（Compose 文件對這種東西指名的答案，
*"to avoid any abrupt visual changes"*）。那個 scope `ArticleMotion` 本來就握著，
只是一直是 private。行為跟旁邊三個 modifier 一模一樣：沒有人提供 motion 時它什麼都不加，
所以 preview 照畫。只給淡入淡出，不給位移：它浮在一個已經在長大的矩形上面，
而「會動的東西上面再疊一個會動的東西」正是第 33 則把轉場削到只剩一層淡的原因。

**scrim 跟箭頭一起，不分開** —— 不是為了少寫一個 modifier。漸層存在的唯一理由是
讓箭頭在照片上看得見（第 34 則）：先到的漸層是照片上一條沒有內容的黑帶，
先到的箭頭就是漸層要救的那個看不見的箭頭。它們是一個東西，所以拿一個 `Modifier`。

**取捨與限制** —— **這一則一樣沒有機器驗證。** 而且有一件事要講清楚：
`sharedBounds` 在配對成功時（`isEnabled = { sharedContentState.isMatchFound }`）
本來就會把它的 `enter`／`exit` 套在整棵子樹上，而 `WayBack` 在那棵子樹裡面。
所以這個淡入是疊在那一層之上，不是取代它——兩個 alpha 相乘，曲線會比單一個淡入慢。
在裝置上看到箭頭來得太慢的人，第一個該懷疑的就是這一層。

---

## 37. 返回箭頭不自己淡入——它上面已經有兩層淡了

**症狀** —— 第 36 則上線後，文章頁的返回箭頭畫出來是 alpha 0：東西還在、點得到、
按下去真的會回上一頁，但一個像素都沒畫，連它底下那條 scrim 也一起不見。

**診斷** —— 第 36 則的前提是錯的。它說「沒有人給 `WayBack` enter 與 exit」，
實際上給了兩個，而且兩個都套在整棵子樹上：

1. **文章這個 scene 自己就會淡入。** `CardBecomesArticle` 把 `fadeIn()` 掛在
   `NavDisplay.TransitionKey` 上，`AnimatedContent` 會把它交給 `AnimatedEnterExitImpl`，
   套在整個進場 scene 的 `Layout` 上——`DetailScreen` 跟 `WayBack` 都在那個 `Layout` 裡面。
2. **`sharedBounds` 配對成功時也會。** `SharedTransitionScope` 的實作是
   `animatedVisibilityScope.transition.createModifier(enter, exit, isEnabled = { sharedContentState.isMatchFound })`，
   而 `WayBack` 就在 `sharedArticleCard` 那棵子樹裡（第 36 則自己也寫下了這一點）。

三層共用同一個 `Transition<EnterExitState>`。差別在**有沒有閘**：`sharedBounds` 那一層
沒配對就把整個 layer 關掉（`isEnabled`），scene 那一層本來就是讓畫面看得見的那一層；
而 `AnimatedVisibilityScope.animateEnterExit` 的 `isEnabled` 是寫死的 `{ true }`
（`AnimatedVisibility.kt`），所以它在這個畫面活著的每一幀都掛著一個 `placeWithLayer`，
alpha 是「scene transition 現在在哪個 `EnterExitState`」的函數——而 `PreEnter` 與
`PostExit` 兩個狀態的值，就是 `fadeIn`／`fadeOut` 自己的 alpha，也就是 0
（`EnterExitTransition.kt` 的 `createGraphicsLayerBlock`）。layout 還在、`IconButton`
的 bounds 還在，所以還點得到。看到的正是這個。

**選了** —— 把 `Modifier.appearsWithTheArticle()` 整個刪掉，讓上面那兩層做它們本來就在做的事。
第 36 則想解決的「箭頭第一幀就在那裡」本來就不存在：它一直是跟著文章的 frame 一起淡進來的。

**當時還考慮**

- **留著 `animateEnterExit`，但也加一個 `isEnabled` 的閘。** 沒得加——那個參數在
  `animateEnterExit` 上不是公開的，`createModifier` 才有，而它是 internal。
- **把 `WayBack` 移進三個狀態裡面，變成內容的一部分。** 不行。它要浮在 Loading／Failed／
  Content 三個之上，這正是第 34 則把它放在 `Box` 兄弟位置的理由。
- **兩個機制都留著「比較保險」。** 這次的缺陷就是這樣來的：兩個 alpha 相乘，
  其中一個沒有閘。

**取捨與限制** —— **一樣沒有機器驗證，而且這一則的診斷有一段是推論。**
可以從原始碼確定的是：那三層的來源、`sharedBounds` 有閘而 `animateEnterExit` 沒有、
以及 `PreEnter`／`PostExit` 的 alpha 是 0。**沒有**從原始碼重建出「文章已經畫在螢幕上、
scene transition 卻停在 `PreEnter`」的那條狀態序列——這個專案沒有截圖測試，
箭頭現在是不是真的不透明，只有裝置能回答。

---

## 38. 不透明的那一層搬進會飛的矩形裡，因為畫面上只該有一個矩形

**症狀** —— 從文章返回時，文章沒有縮回卡片。容器確實在縮，但它縮的時候背後還立著
一整片螢幕大小的 `background`：讀者看到的是一圈比目標卡片大得多的邊界，
以及一整頁原地淡掉。

**根因** —— 那片背景不在容器裡面。`Mosaic.kt` 是 `EdgeToEdgeScreen { DetailScreen(...) }`、
`EdgeToEdgeScreen` 是 `Surface(Modifier.fillMaxSize(), color = background)`，
而 `sharedArticleCard` 掛在 `DetailScreen` **裡面**。所以畫面上一直有兩個矩形：
一個會飛，一個不會。`sharedBounds` 只裁它自己那一個——飛行途中由
`clipInOverlayDuringTransition` 裁，靜止時由 `roundedBy` 的 `graphicsLayer` 裁——
第二個矩形從頭到尾都不在那個裁切範圍內。

**選了** —— 把 `EdgeToEdgeScreen` 整個刪掉，改由 `DetailScreen` 在自己的容器 bounds
裡畫那一層：`Surface(modifier = container.fillMaxSize(), color = background)`。
從外框一路到卡片，只剩一個矩形。

### 這一則取代第 33 則關於「哪一層不透明」的結論，但不改寫它

33 則講的**只讓一層淡**今天仍然成立，`CardBecomesArticle` 一行都沒動。被取代的是它
順帶固定下來的另一件事：**那個會淡的層是螢幕大小的外框**。`EdgeToEdgeScreen` 的註解
把這件事寫成了它存在的理由——「文章唯一真正需要外框給的東西：一層自己的不透明」，
而 33 則給的理由是「透明的文章會淡在一份還讀得出來的清單上」。

**那個理由沒有錯，錯的是它被放在哪裡。** 遮住清單的那一層必須跟著矩形走，因為容器
轉場的定義就是「一個矩形從卡片長成整頁」；一層不跟著走的不透明，等於宣告那個矩形
只是裝飾。搬進去之後兩件事同時成立：**文章那一端的容器本來就是整個螢幕**，所以靜止
時看到的顏色一個 pixel 都沒變；而飛行途中不透明的範圍正好是矩形自己，清單在矩形
**外面**看得見——那不是把 33 則修掉的缺陷放回來，那正是容器轉場該有的樣子。

**33 則原文保留。** 它記的是當時的判斷，而當時 `EdgeToEdgeScreen` 確實是唯一放得下
那一層的地方（第 34 則同一個 commit 才剛把文章的外框拆掉）。history 是交付物。

### 對齊改成 `TopCenter`

`ResizeMode.scaleToBounds()` 的預設是 `ContentScale.FillWidth` ＋ `Alignment.Center`。
`scaleToBounds` 只量一次——量在它最後要成為的尺寸——之後每一幀把那張已經量好的畫面
縮放進當下的 bounds。以 FillWidth 把整頁文章縮到卡片寬度，高度是卡片的好幾倍，
`Center` 於是把**文章的中段**擺進卡片裡，圖片被推到上緣外面；而圖片自己是另一個
shared element，同一時間正飛向那張卡片的**頂端**。兩者整段轉場都在對「上面是哪裡」
持不同意見。改成 `TopCenter`，頭條卡片的 16:9 圖片與文章的 16:9 圖片剛好重合。

### resize mode 維持 `scaleToBounds`

文件同一段的兩句話往兩個方向拉。`RemeasureToBounds` *"works best for background"*，
而這個矩形現在正好開始畫背景了；但同一段也說它 *"does not work well for layouts with
specific size requirements. Such layouts include Text, and bespoke layouts that could
result in overlapping children when constrained to too small of a size"*。容器裡除了
背景還有一整篇可捲動的文章：標題、摘要、三個句子那麼長的按鈕。把它們每一幀重新用
卡片大小的 constraints 量一次，正是那句話點名的失敗案例，而且是每一幀一次完整
re-layout。**背景是這批貨裡比較小的那一半**，所以跟著 Text 那半邊走。

**當時還考慮**

- **`EdgeToEdgeScreen` 留著，只是不畫背景。** 那它就只剩 `content()` 一行，一個什麼
  都不做的間接層。刪掉，理由搬到 `Mosaic.kt` 的呼叫點與 `Screen` 的註解裡。
- **用 `Modifier.background()` 而不是 `Surface`。** 少一個 layout node、少一層 clip。
  沒選：Material 3 的 `Surface` 還掛著一個 `pointerInput(Unit) {}`，轉場途中兩個 entry
  都活著，換成 `background()` 等於讓一次點擊可能落到底下的清單上。維持原本的行為比
  省一個 node 重要。
- **把 `CardBecomesArticle` 的 `fadeIn()` 一起拿掉。** 現在整個文章畫面都在容器裡，
  而 `sharedBounds` 自己就有 `enter`／`exit`，scene 那一層的淡看起來是多的（第 37 則
  已經記過這兩層會相乘）。沒動：`sharedBounds` 那一層有閘
  （`isEnabled = { sharedContentState.isMatchFound }`），沒配對到卡片時它整層關掉，
  scene 那一層就成了唯一會淡的東西。拿掉它，未配對的情況會變成硬切。

**取捨與限制** —— **一樣沒有機器驗證。** `build detekt lint` 全綠只證明它會編譯。
可以從原始碼確定的是：背景過去確實在 shared bounds 外面（三個檔案讀得出來）、
`scaleToBounds` 那兩個預設值、以及文件那兩句話。**不能確定**的是它動起來好不好看——
這個專案沒有截圖測試，沒有人在裝置上看過。尤其飛行途中清單會從矩形外面露出來，
那是刻意的；如果在裝置上覺得太吵，第一個該懷疑的是這一則，不是 33 則。

---

## 39. 來源與時間那一行跟著飛，但 Saved 不參加

**選了** —— `:core:ui` 多一個 `sharedArticleAttribution`，與 `sharedArticleTitle`
同一個形狀（`sharedBounds` ＋ `scaleToBounds`，因為它是文字）。feed 的頭條與列、
以及文章畫面，三處都掛上它。**Saved 的卡片不掛**，那一行維持交叉淡入淡出。

**為什麼原本是錯的** —— 卡片上有它、文章上也有它，內容一模一樣，而整段轉場裡
圖片在飛、標題在飛，只有它在原地淡掉又淡出來。一個兩端都存在、兩端長得一樣的東西
用交叉淡入淡出，等於宣稱它是兩個不同的東西。

**為什麼 Saved 不能一起** —— 它的那一行**不是同一行**。
`FeedUi.kt` 與 `ArticleView.kt` 都寫 `"$source · ${readableTime(publishedAt)}"`，
而 `SavedRow` 只有 `id`、`title`、`source` 三個欄位，`SavedScreen` 就把 `source` 裸著印。
Saved 的列從來沒有時間——這件事之前沒有任何一則記過，它是那個畫面當初順手做的取捨，
今天才第一次有東西壓在上面。把 `"The Verge"` 與 `"The Verge · 2 hours ago"` 配成
一對 shared bounds，`scaleToBounds` 會量其中一端、把它縮放到另一端的寬度：
讀者會看到來源名稱橫向拉伸，去填滿那個時間戳沒有留下來的空隙。
**字會變的那一行就該交叉淡入淡出**，那正是交叉淡入淡出存在的理由。

**當時還考慮**

- **讓 `SavedRow` 也帶時間，四處統一。** 那不是動畫決定，是改畫面顯示什麼：
  Saved 的列現在認人不認時間，而「一篇存下來的文章什麼時候發布的」對一份已經
  離線的清單說不說得上話，是另一個問題。為了讓一段轉場好看而改畫面內容，順序反了。
- **Saved 那端改用 `RemeasureToBounds`，讓它重新斷行而不是拉伸。** 兩端的 resize
  mode 各自宣告是可以的，但那只是把「拉伸」換成「一行字在飛的途中重新排版」，
  而文件對 Text 的建議正是別這麼做。
- **`Attribution` 直接吃 `ArticleId` 而不是吃 modifier。** 那會讓這個 composable
  知道它正在被誰動畫。維持 modifier：`sharedArticleTitle` 也是這樣接上去的，
  而且呼叫端要不要接是呼叫端的事——這正是 Saved 能單方面不接的原因。

**取捨與限制** —— **一樣沒有機器驗證。** 「拉伸」是從 `scaleToBounds` 的定義推出來的，
不是看到的：這個專案沒有截圖測試，沒有人在裝置上比對過 Saved 與 feed 兩條路徑。

## 40. 電影跟著「日」走，因為那是來源唯一說出口的單位

**選了** —— 第三種來源用 TMDB 的 `/3/trending/movie/day`，freshness 是**一天一次**：
`TrendingMovies` 帶著它被算出來的那個 `forDay`，寫進 `cacheDir`，只有讀者手上的日期
翻過去才值得再問一次。下拉不會觸發它，重開 app 也不會。

**為什麼不能沿用既有的兩條規則** —— README 那一節主張「兩個都不是憑空挑的數字」，
而這兩條都搬不過來：

- **天氣那條**靠回應自己帶的 `interval: 900`。TMDB 的回應裡沒有任何等價欄位——
  沒有時間戳、沒有步長、沒有 `Cache-Control` 值得信任的語意。
- **文章那條**是「有人開口才問」。但下拉的意思是「給我更新的報導」，
  它不會讓 TMDB 重算今天的榜單。把下拉接上去，等於用讀者的流量換一份一模一樣的清單。

來源真正說出口的只有一件事：**位址裡的 `day`**。`/trending/movie/{time_window}`
的另一個值是 `week`，所以「多久換一次」是被請求指定的，只是寫在路徑而不是回應裡。
規則因此仍然不是我們挑的數字。

**為什麼要落地到檔案** —— 不寫下來的話，「一天一次」實際上等於「一次啟動一次」，
那就是文章那條規則換個名字。寫進 `cacheDir` 之後，午餐前開五次 app 只會問一次。

**這條規則會錯在哪裡** —— TMDB 沒有文件說它在哪個小時翻日。所以在讀者本地午夜之後
的第一次請求，可能拿回昨天那份榜單——**一天最多浪費一次請求**。這比天氣原本那個
固定十分鐘窗好得多：那個窗保證三分之一的請求拿回同一個數字。

### 圖片 base URL 寫死，不打 `/3/configuration`

TMDB 文件明說 base 與尺寸清單「可以呼叫 `/configuration` 取得」。**沒有那樣做。**
理由是它要在第一張海報畫出來之前多插一次請求，而且是每次沒有快取的啟動都插一次，
換來的是一個在這個 API 的生命週期裡沒變過的字串。寫死的是
`https://image.tmdb.org/t/p/w342`，`w342` 是 116dp 的海報在 3x 螢幕上需要的寬度。

**代價寫清楚**：TMDB 哪天換 CDN 網域或砍掉 `w342`，海報會集體變成空白磚
（不會崩，`AsyncImage` 失敗就是不畫）。真正該做的修法是把 configuration 抓一次、
存起來、幾天檢查一次；那是一整條快取路徑，而這份作業裡它換不到任何看得見的東西。

### 沒有 token 的 checkout 要能建置，而且不是錯誤狀態

token 放在 `local.properties` 的 `tmdb.token`，git 忽略它。`:core:data` 開
`buildConfig = true`，缺值時 build config field 是空字串——**不是 build 失敗**，
因為作業要求乾淨 checkout 一行指令就能建。`DataModule` 讀到空字串就給 `NoMovies`，
它永遠回空清單、永遠不發請求；畫面上就是**沒有那一列**，跟天氣讀不到時沒有那張卡
同一個處置。不是錯誤畫面，不是佔位圖，是不存在。

**要說清楚的是這不叫「保密」**：有 token 的 build 會把它編進 APK，任何 client-side
key 都一樣，反編譯就看得到。把它擋在 repository 外面防的是「金鑰進版控」，
不是「金鑰在裝置上不可讀」。TMDB 的 read access token 只能讀公開資料，這個取捨可以接受；
如果哪天需要寫入權限的 token，正確答案是它根本不該在 app 裡。

**當時還考慮**

- **一天一次改成「跟著 app 啟動」，跟文章同一條。** 那就沒有第三條規則了，
  而作業把 freshness 列為評分項的理由正是不同來源節奏不同。
- **`week` 而不是 `day`。** 更新更慢、更省流量。沒選：作業的參考畫面寫的是
  「Trending Movies」，而 day 是 TMDB 對這個詞的預設值；而且一週才動一次的一列，
  讀者連續七天看到的是同一份東西。
- **空回應當成失敗、一分鐘後重試。** 沒選：那正是這個 app 的 freshness 政策在反對的
  花費模式。`results` 整個不見是另一回事，那在解析階段就丟例外，不會被當成「今天沒東西」。
- **給海報一個點擊目標，或做參考畫面上的 "See All"。** 沒選：兩者後面都沒有畫面。
  一個按下去會變暗然後什麼都不發生的卡片，比沒有那個 affordance 更糟。
- **`vote_average` 直接顯示，不管 `vote_count`。** 沒選：TMDB 對沒人投票的片子送 0，
  而「0.0 / 10」是一句對這部片的評論。當天上映又進榜的片剛好就是這一列。
  所以 `Movie.rating` 是 nullable，沒有分數就不畫那個徽章。
- **分數在 mapper 就四捨五入成 8.1。** 沒選：小數點幾位是畫面的決定，
  跟 `ArticleItem` 存 `Instant` 而不是「2 hours ago」同一條線。domain 收 8.117，
  view model 產出 "8.1"，兩邊各有各的測試。

**取捨與限制** —— **這一列從來沒有跟真實資料一起被看過。** 這台機器沒有網路去打 TMDB，
所以海報實際載進來長什麼樣、`w342` 在 116dp 上夠不夠銳利、
一列裡第三張被切掉的位置對不對，全部只有裝置能回答。可以從原始碼與 TMDB 文件確定的是：
endpoint 路徑與 `day`／`week` 兩個值、bearer 認證的位置、
`image.tmdb.org/t/p/` 這個 base 與 `w342` 在尺寸清單裡、以及回應裡那幾個欄位名。
freshness 規則本身有測試釘住——它數的是請求次數，不是畫面。
---

## 41. feed 剛剛畫過的那一篇，文章畫面不再重問一次

**症狀** —— 打開任何一篇文章都先看到轉圈，而且**容器轉場只有回程是對的**。
進去的時候圖片、標題與來源那三個 shared element 在文章這端根本不存在——它們只畫在
`DetailUiState.Content` 那個分支裡——沒有配對就不飛（第 37 則講的
`isMatchFound` 是同一個閘）。容器矩形自己還是會長大，因為 `sharedArticleCard`
掛在畫面上而不是掛在它的某一個狀態上（第 38 則），所以讀者看到的是
**一張卡片長成一個裝著轉圈的空矩形**。回程看起來對，是因為那時候資料已經在了。

**根因** —— feed 那張卡片不是「一篇文章的摘要」，它**就是一個 `ArticleItem` 被畫出來**。
但跨到文章畫面的只有 id：`ArticleKey` 要能寫進 Bundle，所以它只帶得動可序列化的東西。
`DetailViewModel.open(id)` 因此只能重問一次，而重問就是那個轉圈。

**選了** —— `:core:data` 多一個 `ArticlesTheFeedShowed`。
`SavedFirstArticleRepository.articles()` 每交出一頁就把那一頁記下來，
`article(id)` 的來源順序變成**存過的那份 → feed 正在顯示的那份 → 網路**。
`:feature:feed` 一個字都沒改——`ArticlePagingSource` 還是只會要一頁、拿到一頁。

**為什麼在 data 層** —— 「一篇文章從哪裡來」第 30 則已經定案由 repository 回答，
而且理由是可複製的：規則寫在畫面裡，第二個要顯示文章的地方就得再抄一次。
第三個來源長出來的時候，那條理由沒有變。

**什麼在替它設界（這一則的重點）** —— 不是 TTL，是**清單本身**。

- 放進去的只有一種東西：repository 交給 feed 的那一頁。
- 拿走它的也只有一種事：**有人要清單的最上面**（`after == null`）。
  而那件事只會由兩個動作觸發——app 啟動，或讀者下拉（第 25 則）——所以
  「記得的東西」與「螢幕上那一代清單」是同一個生命週期。
- 讀者只點得到螢幕上有的卡片。所以它**答得出來的一定是讀者正在看的那一份**，
  而且**不可能比螢幕還舊**。

這是刻意不做成第四條 freshness 規則。README 那一節的主張是「三個都不是憑空挑的數字」，
在這裡放一個 TTL 就正好是那個主張反對的東西——而且它量的是**讀者眼前那張卡片的年紀**，
那個數字不需要被量。

**它只在記憶體裡。** 程序被殺掉之後回來，裡面是空的——那正是 Loading 存在的情境，
而第 25 則已經裁決過「會被殺掉基本上代表他很久沒用這個 app，回來重抓是合理的」。
**Loading 這條路一行都沒有被刪掉**，`OneArticleTest` 有一條測試就叫
`an article the feed never showed is still asked for`。

**沒有上限，理由不是忘了** —— 它拿的是 Paging 為同一份清單本來就握著的那些
`ArticleItem` 物件（`PagingConfig` 沒有設 `maxSize`），所以多出來的是每篇一個 map entry，
不是第二份文章。而且清空的時機跟 Paging 丟掉舊世代的時機是同一個。
**它長不過它照著的那份清單**，這比一個挑出來的容量數字誠實。

**順手修掉的第二個轉圈** —— `DetailViewModel` 原本在 `Content` 之前
`kept.saved.first()`，那是**第二個 Room 查詢**，而且它擋在文章前面。
現在改讀 `init` 那個 collector 已經在看的最新值（`keptNow`）。
「這篇存了嗎」還是照舊會更新——它本來就是那個 collector 的工作——只是不再由
「這篇是什麼」等它。開一篇文章因此從「一次 Room 查詢 ＋ 一次 Room 查詢 ＋ 一次網路」
變成「一次 Room 查詢」，而那一次是第 30 則要求的。

**當時還考慮**

- **把文章的欄位塞進 nav key。** `ArticleKey` 要序列化進 Bundle，`TransactionTooLargeException`
  是拿整篇文章當導覽參數的標準結局；而且它會讓「畫面之間傳什麼」跟「文章長什麼樣」綁在一起，
  domain 的欄位一動，返回堆疊的序列化格式就跟著動。key 要小，第 32 則加 `from` 的時候
  就是照這條線畫的。
- **拉一個共用的 view model（activity scope 或共用 graph）。** 那正好是
  `Mosaic.kt` 用 `rememberViewModelStoreNavEntryDecorator` 擋掉的東西：
  一個共用的 view model 會讓兩篇文章共用一個物件，回上一篇時裡面裝的是下一篇。
- **`DetailScreen` 多收一個「已經知道的文章」參數。** 那把來源仲裁搬回畫面上，
  第 30 則整則就是在反對這件事；而且 `Mosaic.kt` 得先有那篇文章才傳得下去，
  它沒有——它只有 back stack 上的 key。
- **記憶體那份排在存過的那份前面。** 那樣「讀者看到哪一份」會取決於 feed 有沒有剛好
  捲過那一篇，那不是一條記得住的規則。第 30 則的取捨（存過的不跟著來源更新）維持原樣。
- **給它一個 TTL 或一個容量上限。** 見上：兩個都是憑空挑的數字，而清單本身已經是界線。

**取捨與限制**

- **一樣沒有機器驗證動起來對不對。** 測試釘住的是「沒有發出那個請求」與
  「不等閱讀清單」，不是「圖片真的飛了」。這個專案沒有截圖測試，
  三個 shared element 在 `Content` 第一幀就配對到了沒有，只有裝置能回答。
- **仍然有一次 Room 查詢擋在前面。** 第 30 則要求存過的那份先答，而那是一次
  `find(id)`。它是本機的、走主鍵索引的，但它是一次 dispatch——所以嚴格說
  `Content` 不保證落在第一幀，只保證不再落在一次網路往返之後。
- **`ArticlesTheFeedShowed` 用 `@Synchronized`。** Paging 在它自己的 dispatcher 上寫，
  文章畫面在 Main 上讀，這是真的跨執行緒。沒有測試證明那個競態被擋住——
  它證明的是單執行緒下的行為。
---

## 42. 底部那條 bar 搬到 `NavDisplay` 外面，因為它是讀者「拿來導覽的東西」

**症狀** —— 從 Reading 切到 Saved，**整條 bar 跟著畫面一起滑走再滑進來**。
打開一篇文章，bar 也跟著整個畫面一起被帶走。它明明是那個「不動的、讀者用來換地方」的
東西，卻每次換地方都自己也換了一次。

**根因** —— `Mosaic.kt` 每個 entry 都給 `Screen(bar = { DestinationBar(...) })`，
而 `Screen` 把它放進 `Scaffold(bottomBar = ...)`——**在 `NavEntry` 裡面**。
`NavDisplay` 轉場的單位是 entry，所以 bar 是被轉場的東西之一。
它甚至被畫了兩份：離場那個 entry 一份、進場那個 entry 一份，兩份一起滑。

**選了** —— `Scaffold` 移到 `NavDisplay` 外面（也在 `SharedTransitionLayout` 裡面）：

```kotlin
SharedTransitionLayout(modifier) {
    Scaffold(bottomBar = { /* … */ }) { padding ->
        NavDisplay(modifier = Modifier.padding(padding), …)
    }
}
```

`Screen` 的 `bar` 參數刪掉，它現在只剩頂欄。

**為什麼 `SharedTransitionLayout` 在最外面** —— shared element 飛行時會被抬進一層
overlay，而那層 overlay 是 `SharedTransitionLayout` 自己的範圍。放進 `Scaffold` 的內容裡，
overlay 就只剩「扣掉 bar 之後」那一塊；而文章那一端的容器**是整個螢幕**，卡片飛過去的
路徑會經過 bar 所在的那條帶子。所以順序是 `SharedTransitionLayout` → `Scaffold` → `NavDisplay`。
overlay 因此畫在 bar 之上，長大的文章會蓋過 bar，而不是滑到它底下。

**bar 在不在，讀 back stack，不傳旗標** —— 文章沒有 bar（第 34 則），
而「現在是不是文章」堆疊上早就寫著了：**文章是唯一一個「疊在某個目的地上面」而不是
「取代某個目的地」的 key**。所以

- `showsTheBar()` 看最上面那個 key 是不是兩個目的地之一；
- `destination()` 從上往下找第一個目的地——**不是只看最上面**，因為 bar 在滑走的那段
  時間裡，最上面那個正是文章，而它得繼續說得出讀者在哪裡。

畫面一個旗標都沒有多收。多收一個旗標的問題不是麻煩，是**它會跟堆疊講不一樣的話**，
而真的那一份是堆疊。

**bar 會自己動：滑進滑出，不是瞬間消失** —— `AnimatedVisibility` ＋
`slideInVertically`／`slideOutVertically`。理由有兩個：

1. **點下卡片的那一幀就讓 bar 消失，會是整段轉場裡最吵的東西。** 那時候卡片還是卡片大小，
   容器轉場才剛開始；一個閃掉的東西比一個長大的矩形更抓眼睛，而讀者該跟著看的是後者。
2. **滑動不改變它被量到的高度**，所以整段離場期間 bar 的位置都還被保留著，
   底下的清單不會在讀者看得到的時候重新排版。版面真正改變的那一瞬間是 bar 已經整個離場，
   而那時候文章自己那層不透明的矩形（第 38 則）已經是整個畫面。

換句話說：**動畫不是裝飾，它是讓 layout 的那一次跳變發生在看不見的時候。**

**inset 只付一次** —— 這是搬動這條 bar 最容易搞砸的地方。付款表：

| inset | 誰付 |
|---|---|
| 狀態列 | `CenterAlignedTopAppBar` 自己（Reading／Saved）；`DetailScreen` 自己（文章，第 34 則） |
| 導覽列 | `DestinationBar` 自己的 `windowInsetsPadding`——所以它「量到的高度」本來就含了導覽列；外層 `Scaffold` 把那個高度扣給 `NavDisplay` |
| 左右 | `Screen` 那層 `Scaffold` |

因此兩個 `Scaffold` 都改了 `contentWindowInsets`：

- **外層設 0。** 預設是 `systemBars`，那樣「沒有 bar 的時候」它會自己補一段狀態列與導覽列
  的 inset——文章就再也貼不到螢幕邊，第 34 則整則會被這一行推翻。
- **內層 `Screen` 只留左右。** 它原本靠「bottomBar 存在」把導覽列吃掉；bar 搬走之後，
  預設的 `systemBars` 會讓它**再付一次**導覽列——外層已經給了含導覽列的 bar 高度，
  清單底下就會多出一條空隙。這正是 edge-to-edge 指南講的重複付款。

**當時還考慮**

- **傳一個 `hasBar: Boolean` 給 `Mosaic` 或每個 entry。** 見上：兩份真相，而其中一份是抄的。
- **不做動畫，`if (showsTheBar()) DestinationBar(...)`。** 最小的改法，也是最吵的：
  bar 會在容器還是卡片大小的時候憑空不見，而且清單同一幀變高。
- **用 `shrinkVertically()` 讓高度跟著動，清單平滑地長進那塊空間。** 沒選：
  那讓底下的清單在整段轉場裡**一直在重新排版**，而第 33 則的立場是同一時間只該有一層在動。
  滑動把那次跳變壓縮成一瞬間，並且藏在文章底下。
- **bar 放在 `SharedTransitionLayout` 外面（`MainActivity` 或 `Mosaic` 的最外層 Column）。**
  那樣 bar 會畫在 shared element 的 overlay **之上**，長大中的文章會滑到 bar 底下，
  等於宣告那個矩形不是整個畫面。
- **讓 pill 的移動也做動畫。** 沒動：那是改畫面顯示的東西，不是這一則的題目。
  現在切換的瞬間 pill 直接換位，跟原本「整條 bar 換一份」看到的東西一樣多。

**取捨與限制**

- **一樣沒有機器驗證。** 有測試的只有「bar 在不在、亮的是哪一個」這個讀取
  （`BackStackTest`，`:navigation` 的第一批測試）。**bar 滑走的時機跟文章長大的時機
  對不對得上、清單有沒有在看得見的時候跳一下、橫向 inset 在有瀏海的機器上落在哪裡**，
  全部只有裝置能回答。這個專案沒有截圖測試。
- **兩條 bar 的動畫曲線沒有對齊過。** `AnimatedVisibility` 用的是 slide 的預設 spring，
  `NavDisplay` 的轉場用的是它自己的規格。看起來合不合，同樣只有裝置說了算。
- **`Screen` 從此只有一個入口。** 第 34 則的 `EdgeToEdgeScreen` 在第 38 則已經刪掉，
  這一則之後 `Screen` 連 `bar` 參數都沒有了——它就是「頂欄 ＋ 內容」，沒有別的模式。

---

## 43. 圓角只有配對到的那一張卡片會動，其餘每一張站著不動

**症狀** —— 把模擬器的 `animator_duration_scale` 調成 10 再點一張卡片，
**清單裡每一張卡片都在把自己的圓角收平**，不是只有被點的那一張。
截到的一幀裡，被點那張下面第三張已經接近方角，它旁邊那張還是圓的——
那不是「只有一張在動」，那是同一條曲線上的兩個不同位置。

**根因** —— `sharedArticleCard` 的半徑是從 `visibility.transition` 算出來的（第 35 則），
而那個 transition 是 `AnimatedContent` 發給**整個 entry** 的，不是發給某一張卡片的。
清單裡每一張卡片都呼叫 `sharedArticleCard`，於是每一張都跟著跑
`Visible → PostExit`（回程 `PreEnter → Visible`），每一張都算出「20dp 收到 0dp」
這條同樣的曲線，而且每一張都畫了出來。真的在飛的只有一張。

第 37 則已經把這個閘的名字寫下來過：`sharedBounds` 自己的 enter／exit 是
`isEnabled = { sharedContentState.isMatchFound }`，沒配對就整層關掉。
**這個檔案裡唯一沒有接上那個閘的東西，就是圓角。**

**選了** —— `roundedBy` 多收兩樣東西：這張卡片的 `SharedContentState`，
以及「這一端靜止時的半徑」。畫的時候問 `isMatchFound`：配對到就讀動畫值，
沒配對就用靜止值。沒有配對的卡片因此一幀都不動。

**閘要在 draw 讀，不能在 composition 讀** —— 這是這一則唯一需要小心的地方。
`isMatchFound` 在宣告這個元素的那一次 composition 裡是 false，要等**對面那一端**
也 compose 完才會變 true；而清單裡的卡片是在 `LazyColumn` 的 measure 階段才 compose 的。
這一段它自己的文件就寫了：*"[isMatchFound] is only set to true _after_ a new
[sharedElement]/[sharedBounds] of the same [key] has been composed. If the new
[sharedBounds]/[sharedElement] is declared in subcomposition (e.g. a LazyList) where
the composition happens as a part of the measure/layout pass, that's when
[isMatchFound] will become true."*

Compose 自己的 `sharedBounds` 撞到的是同一件事，而它的解法是**根本不在 composition
裡讀**：把旗標包成 lambda 往下傳（`isEnabled = { sharedContentState.isMatchFound }`），
原始碼旁邊那段註解講得很直白——*"Since we don't know if a match is found when this is
composed, we have to defer the decision to enable or disable content scaling until
later in the frame."* `graphicsLayer` 的 block 正好就是「這一幀稍後」，
所以這裡照抄它：閘在 layer block 裡讀。

在 composition 裡讀會壞在**進場那一端**——它會先畫一幀「終點的半徑」，
下一幀才跳回動畫的起點。那是一個發生在讀者正盯著看動作的瞬間的跳變。

**當時還考慮**

- **在 composition 裡 `if (isMatchFound) 動畫值 else 靜止值`。** 見上。
- **讓 `animateDp` 的 `targetValueByState` 讀這個旗標**（沒配對就兩個狀態都回自己這端）。
  更糟：transition 的初始值在它開始的那一刻就定住了，旗標後來變 true 也追不回來，
  進場那一端會整段不動。
- **沒配對就不呼叫 `animateDp`。** 那會讓 composition 的形狀跟著一個「飛行途中會改變」的
  值走，等於在轉場中間增刪 transition 的動畫。現在的做法是**一律註冊，只在畫的時候決定
  要不要讀它**——沒配對的卡片多一個永遠不會被看的動畫，換到的是 composition 穩定。
- **只在 feed 那一端加條件，文章那端不管。** 兩端規則就不一樣了，而第 35 則整則的立論是
  「兩端各自算，算出同一個數字」。閘也必須是兩端同一個。

**取捨與限制**

- **一樣沒有機器驗證。** 這個專案沒有截圖測試，這一則是在裝置上看出來的，也只能在
  裝置上看回去。做法是 `animator_duration_scale=10` ＋ 逐幀 `screencap`，
  量一張未被點的縮圖左上角被切掉多少個背景色像素：
  修改前是 891 → 891 → 740（角在整平），修改後是 952 → 952 → 952（不動）。
  那是一次手動觀察，不是一個會自己跑的檢查。
- **`animateDp` 仍然為每一張卡片註冊。** 見上，這是刻意的。
- **這個閘有一道細縫，是第二個模型（Codex）找出來的，已經補上。**
  `isMatchFound` 是三樣東西的 or，其中只有兩樣是 snapshot state：第三樣
  `activeMatchDeferred` 讀的是 `requestToBeHandled`，一個普通欄位。
  最初的寫法是 `if (isMatchFound) radius.value else standingStill`——沒配對的那一支
  **不會讀 `radius`**，於是一張「配對是從那道門進來的」卡片，layer 的 read set 裡
  沒有任何東西會再變，它就停在靜止半徑上。改成先把 `radius.value` 讀出來再判斷：
  動畫值因此永遠在 read set 裡，轉場期間每一張卡片每一幀都會重跑一次 layer block，
  而那正是旗標可能改變的唯一時段。代價是每張卡片每幀一次比較，換到的是
  「在 draw 讀就一定看得到」這句話真的成立。
  **這條細縫沒有在裝置上重現過**——它是讀 Compose 原始碼推出來的，
  補它的理由是它便宜，不是它被看見過。

---

## 44. 圖片的圓角看它坐在哪裡，而「容器一律裁切」只對其中一種卡片成立

> **這一則取代 `df926eb`（*fix(ui): let the card decide what shape the picture it
> holds is*）的結論，但不改寫它。** 那個 commit 把規則收成一句「圖片一律不宣告形狀，
> 由容器裁」——而那句話從來沒有進過 `DECISIONS.md`，它只寫在 commit 訊息與
> `:core:ui` 的 KDoc 裡。所以這一則同時做兩件事：把它補記進來，並且改掉它。
> 更早的第 35 則讓 `sharedArticleImage` 收一個 `ArticleEnd`，那條也一併被取代。

**症狀** —— feed 的列（`StoryRow`）縮圖是**方角**。設計稿裡它是圓角的
（`shapes.medium`，16dp），`b65eabe` 之前的程式碼也是。

**根因** —— 「由容器裁」在頭條卡片上是對的：那張照片貼著卡片的左、右與上緣，
它要的兩個角就是卡片自己的上面兩個角，而卡片正在把那兩個角從 20dp 收到 0dp。
文章那一端是同一個安排大一號。

**列的縮圖不是那樣。** 它是一個 92dp 的方塊，上下各離列的邊界 4dp，右邊隔著
14dp 與一整欄字；唯一跟列共用的是左緣，而那是「92dp 的邊」對上「20dp 的角」。
所以它有三個角在容器的 clip 碰不到的地方，第四個只被輕輕擦過——
**「被容器裁」在它身上等於沒有裁。**

**選了** —— `sharedArticleImage` 收一個 `PictureSeat`：
`MEETING_AN_EDGE`（貼著容器的邊，不宣告形狀，由容器裁）與
`STANDING_ALONE`（誰也碰不到，自己圓）。頭條的照片與文章的照片是前者，
列的縮圖是後者，半徑用 `shapes.medium`——那是設計稿給內縮小圖的角，
不是卡片的 20dp，因為**它不是卡片**。

**Saved 沒有圖片可以判斷** —— 用同一條規則檢查閱讀清單的卡片，得到的答案是
**它一張圖都沒有**：`SavedRow` 只帶 `title` 與 `source`（第 39 則寫的是同一件事的
另一半）。所以這一則對 Saved 沒有任何影響，而不是「Saved 也照這條規則走」。

**這條規則到不了的地方，以及為什麼** —— **去程時，列的縮圖在飛行的第一幀就變成方角。**
`sharedElement` 只畫**正在進場**的那一端（`renderOnlyWhenVisible`，第 35 則寫過），
去程進場的是文章那張照片，而**它不知道讀者是從頭條點進來還是從列點進來**：
兩邊的 key 一樣、id 一樣，跨過導覽邊界的只有 id。

這不是推論，兩種做法都在裝置上跑過：

- 讓文章那一端也帶一個「會收掉的半徑」（16dp → 0）。從**列**點進去對了，圓角一路連續；
  但從**頭條**點進去，照片下緣的兩個角在大半段飛行裡都是圓的，在照片與內文之間切出
  兩個缺口——正是 `df926eb` 修掉的那個缺陷，換到空中重演一次。
- 兩個缺陷之間留下前者。頭條那張是這個 app 最大的一張照片，缺口持續大半段飛行；
  縮圖那個是一幀的不連續。

**回程沒有這個問題**：回程進場的是 feed，縮圖自己的 clip 一路都在，
照片從整個螢幕縮回來的過程中圓角是連續的（裝置上確認過）。

**當時還考慮**

- **把「坐在哪裡」也跨過導覽邊界傳給文章畫面。** 那要 feed 在 `onOpenArticle` 上多送
  一個參數、`:navigation` 存著它、再從 `ProvideArticleMotion` 發下去——為了一個角，
  讓 feature 開始知道自己正在被誰動畫，而第 32 則的立場正好相反。
- **從 bounds 的寬度推半徑**（「照片跟容器一樣寬，就是貼著邊」）。第 35 則為了卡片拒絕過
  這一招，理由是寬度分不開卡片的兩端；對**照片**它其實分得開。沒選的理由不同：
  它要嘛得知道容器當下的寬度（只有 `parentSharedContentState.clipPathInOverlay` 那個
  `Path` 拿得到，而那依賴 draw 期間的隱含順序），要嘛得自己挑一條曲線——
  而挑曲線就是挑一個沒有依據的參數。
- **讓縮圖也用 `sharedBounds`，兩端都畫、互相淡入淡出。** 那是同一張照片淡進同一張照片，
  而 `sharedArticleImage` 是 `sharedElement` 的理由正好是「兩端是同一張照片，
  沒有東西要淡」。

**取捨與限制**

- **靜止時是對的，飛行途中有一個已知的不連續**（見上）。這個專案沒有截圖測試，
  所以這句話的證據是 `animator_duration_scale=10` 下逐幀 `screencap` 的畫面，
  不是任何一個會自己跑的檢查。
- **角的來源從此有兩個。** 卡片的 20dp 在 `:core:ui` 寫死，縮圖的角走主題的
  `shapes.medium`。這是刻意的：卡片的角是轉場的一端，必須跟另一端對齊；
  縮圖的角不是任何一端，它就是設計稿給一張內縮小圖的角。
