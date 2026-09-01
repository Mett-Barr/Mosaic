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

## 26. 一個問題，兩個後果——而不是兩個呼叫者

**這一則是使用者主導的**：他指出「有兩個觸發源就不合理」，而那正是問題所在。

**選了** —— `ArticleRepository` 五個成員，取頁的只有兩個：

```kotlin
suspend fun firstPage(): ArticlesResult                     // 給我清單頂端
suspend fun nextPage(after: PageCursor): ArticlesResult     // 給我這之後那頁
suspend fun refreshFirstPage()                              // 去拿更新的（讀者要求）
val changed: Flow<Unit>                                     // 換掉了正在被看的那份
suspend fun article(id: ArticleId): ArticleResult
```

**問題長什麼樣** —— 冷啟動要「先顯示磁碟上那頁，同時去拿新的」。我第一版把它做成
**兩個呼叫者**：`PagingSource` 問 `firstPage()`，ViewModel 另外呼叫 `refreshFirstPage()`。

首次安裝時磁碟是空的，兩者塌成同一件事——**兩個一模一樣的請求**。

那不是意外，是「**決定何時花使用者流量**」這個職責被切成兩半的症狀。

**修法不是去重，是收回** —— `firstPage()` 自己承擔兩個後果：手上有磁碟那份就立刻給並
去拿新的，手上沒有就去拿並等它。**只有 repository 知道手上這份是不是自己剛抓回來的**，
所以只有它能決定要不要去看。

**`force` 沒有回來** —— 它當初存在，是因為「冷啟動」和「讀者下拉」共用同一個函式，
需要一個布林分辨。現在它們是**不同的成員**：一個是問句，一個是命令。
意圖寫在名字上，不是寫在參數上（`DECISIONS.md` 25 拆掉它時的教訓）。

**`changed` 為什麼在第一次到達時不發** —— 沒有人在看舊的那份。發了只會要求畫面
重畫它已經在畫的東西。

**scope 是注入的，而且活得比畫面久** —— 已經付出去的請求不該因為讀者離開畫面而被取消。
跟天氣同一個模式（`DECISIONS.md` 24）。

**裝置上驗過兩條路徑**：清空資料後冷啟動（等網路）、`force-stop` 後冷啟動（三秒內就有
內容，來自磁碟）。

## 27. 分頁的重複在客戶端擋，遺漏擋不住——而且不假裝擋得住

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

