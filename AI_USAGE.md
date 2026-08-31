# AI 使用紀錄

agent 實際產出了什麼、後來怎麼了。`AGENTS.md` 寫規則，這份檔案記錄規則有沒有守住。

寫進來的時機有兩種：審查否決或重寫了某個產出時，以及 gate 抓到 agent 已回報「完成」
的錯誤時。原樣接受的產出不逐筆記錄——那是預設情況，逐筆記反而會把值得看的幾則埋掉。

---

## 紀錄

### 骨架階段——gate 抓到兩個 agent 沒抓到的錯

兩個都是在實際跑之前就被回報為「已完成」。

1. **跳脫序列被吃掉。** agent 透過 shell heredoc 寫 `settings.gradle.kts`，
   shell 把 `\\` 收斂成 `\`，Kotlin 因此拒絕 `"com\.android.*"`（unsupported escape
   sequence）。同一個問題也悄悄弄壞了 `local.properties`。

2. **一道「失敗時回報成功」的 gate。** 第一次跑 gate 用了管線
   （`./gradlew build … | tail`），於是 shell 回報的是 `tail` 的結束碼而不是 Gradle 的。
   畫面上印著 `BUILD FAILED`，結束碼卻是 `0`。修法是設 `pipefail` 並明確印出
   `${PIPESTATUS[0]}`。

第二個是比較有意思的那個：**驗證機制自己是壞的，所以綠燈不代表任何事**。
一道 gate 的價值上限就是它偵測失敗的能力——確認一道 gate「真的會失敗」，
是信任它的前提。

### 骨架階段——第二個模型的獨立審查

| | |
|---|---|
| **審查者** | Codex，`gpt-5.6-luna`，reasoning effort `max` |
| **範圍** | `f2c3a51`…`3eb8f0b` 三個 commit，加上當時未提交的工作樹 |
| **指令** | 只找反對意見；唯讀，不得改檔 |
| **驗證** | `git diff --stat` 確認它沒有動過任何檔案 |

它提出 8 項「必須修」與 5 項提醒。逐項處置：

| # | 它說的 | 處置 |
|---|---|---|
| 1 | 「先看過紅燈」沒有任何證據，因為測試與實作會落在同一個 commit | **採納。**`AGENTS.md` 改成測試與實作**分兩個 commit**，`test:` 那個刻意留紅、失敗輸出貼進 body |
| 2 | `AI_USAGE.md` 沒有可追溯的逐項紀錄，評審無法驗證人真的審過 | **採納。**就是這一則的格式 |
| 3 | 「兩個模型」那段沒有實際產出佐證，讀起來像拿工具數量充場面 | **採納。**同上——這一則就是它要求的佐證 |
| 4 | 七模組的理由過度宣稱：編譯器擋不住 feature 亂相依 | **部分採納。**收窄 `DECISIONS.md` 的說法，並補 Konsist 架構測試把規則變成可執行的。**不採納**「合併回單一模組」 |
| 5 | `allWarningsAsErrors` 只涵蓋 Kotlin，文件寫得像涵蓋全部 | **採納。**措辭改精確，並明說 lint warning 不擋建置 |
| 6 | JDK 沒被釘住，`jvmTarget` 不等於指定用哪個 JDK 編譯 | **採納。**加 `jvmToolchain(17)` 與 foojay resolver |
| 7 | 模組相依圖沒有任何機制驗證它是最新的 | **採納。**CI 加 `createModuleGraph` 後 `git diff --exit-code` |
| 8 | 文件宣稱「每個 commit 都符合規則」，但規則是第二個 commit 才建立的 | **採納。**改成「從規則被寫下的那個 commit 起」 |

**否決一項**：它說 `.gitattributes` 沒生效、`gradlew` 可能在 Linux 上 shebang 失效。
實測 `git ls-files --eol` 顯示 `i/lf w/crlf`——**index 是 LF**，CI checkout 拿到的就是 LF，
Windows 工作樹是 CRLF 無害。它看的是工作樹而不是 index。

**它問的最後一個問題**，我認為是這份作業最該被問倒的地方：

> 請指出一個功能，證明人先定義了驗收條件、先看過測試紅燈、再批准測試；
> 如果測試與實作在同一個 commit，這個證據在哪裡？

第 1 項的改動就是為了讓這個問題有 `git log` 可以回答。在那之前，我沒有答案。

### 文章模型——第二個模型的獨立審查

| | |
|---|---|
| **審查者** | Codex，`gpt-5.6-luna`，reasoning effort `max` |
| **範圍** | 分支 `feat/feed-item-model`：`test:` commit 加上當時尚未提交的 `ArticleItem.kt` |
| **指令** | 只找反對意見；唯讀，不得改檔、不得建立腳本、不得轉包給其他 AI CLI |
| **驗證** | `git status --porcelain` 只列出我自己的未追蹤實作，它沒有動過任何檔案 |

| # | 它說的 | 處置 |
|---|---|---|
| 1 | 測試可被「建構子一律丟例外」的假實作蒙混——三個案例裡沒有一個走成功路徑 | **採納。**補上合法文章的斷言，並實測它在無驗證的實作下**通過**、另外兩個失敗 |
| 2 | 只有編譯失敗不算看過測試紅燈；應先用不驗證的實作取得真正的 assertion failure | **採納。**照做，兩份紅燈輸出都貼進 `test:` commit 的 body |
| 3 | `require` 丟出的例外沒有人接：mapper 若直接建構，一筆壞資料會炸掉整頁 | **採納，落在下一個 commit。**契約寫進 `DECISIONS.md` 3，由 mapper 的「一筆壞、一筆好」測試來守 |
| 4 | freshness 要用的 `updatedAt` 與「本機何時抓的」都不在模型裡 | **部分採納。**確實會回來加，但現在不加——沒有行為在用的欄位是投機。留給人裁決 |
| 5 | 架構取捨沒有寫進 `DECISIONS.md` | **採納。**同一個 commit 補上第 3、4、5 則 |
| 6 | `ArticleId(String)` 它沒有反對意見，但提醒跨來源的 `42` 會撞 | **採納提醒。**寫進 `DECISIONS.md` 5 的取捨段 |
| 7 | `java.time.Instant` 它沒有反對意見，但提醒它對 Compose 不 stable | **採納提醒。**寫進 `DECISIONS.md` 4；等模型真的進 UI 再改設定檔 |
| 8 | 未追蹤的實作不會出現在 `git diff`，人審容易只看到測試 | **接受。**整批留在主題分支上，未合併前可整段回退 |

第 1 項是這次唯一改變產出的意見。它抓到的正是 `AGENTS.md` 列的第三個判準
（「assertion 有沒有可能被 hardcode 的回傳值蒙混過去」）——**我自己審過同一份測試，
沒有看出來**。這就是換一個實驗室的模型的價值：它不共用我的盲區。

**這一批的人審缺口**：這幾個 commit 是在人睡著時做的，`AGENTS.md` 第 3、8 兩步
（人審測試、人審 diff）當下沒有發生。授權來自使用者「直接做完」那句話，但授權不等於審過。
待審清單留在 `.open-questions.md`，全部在主題分支上，未合併前可整段回退。

### 網路邊界——第二個模型的獨立審查

同樣的指令與驗證方式（唯讀、只找反對意見、`git status` 確認它沒動過檔）。

| # | 它說的 | 處置 |
|---|---|---|
| 1 | `results: List<ArticleDto>` 是**整批**解碼的，一列型別錯就殺掉整頁——契約根本沒成立 | **採納。**改成逐列解 `JsonElement`。這是它自己選的「只能改一件事」，也確實是最嚴重的一項 |
| 2 | 靜默丟棄：丟掉幾列、為什麼丟，全部消失 | **採納。**丟掉的列帶著原因回到呼叫端。**沒有**改成整頁失敗——部分成功仍然是成功 |
| 3 | `runCatching` 吞掉所有 `Throwable`，含 `OutOfMemoryError` | **採納。**改成只接領域模型丟的 `IllegalArgumentException` 與解析用的兩個明確例外 |
| 4 | DTO 全欄位給預設值，把缺 `url`／`news_site` 偽裝成合法文章 | **採納。**顯示得出文章所需要的欄位不給預設值 |
| 5 | 缺 `results` 的回應會變成「正常空頁」，混淆 empty 與 error 兩個狀態 | **採納。**`results` 不給預設值，缺了就是解碼錯誤 |
| 6 | 測試可被「永遠把 image 設 null／summary 設空字串」的假實作蒙混 | **採納。**補上真實 fixture 的 summary、imageUrl 與三筆的 id／source 斷言 |
| 7 | 我在任務書裡宣稱 `Instant.parse` 不吃 offset——**它說我這個前提是錯的**，附 Java 17 文件連結 | **採納它的更正。**加了一個 `+08:00` 的案例把行為釘住 |
| 8 | `count`／`next` 先留著沒問題，但 `toArticles()` 現在把它們丟掉了，下一個 commit 做分頁會需要 | **接受提醒。**分頁那個 commit 會改成把 page metadata 一起帶出來 |

第 7 項值得單獨記：**我寫錯的前提被它抓出來並附了出處**。審查的價值不只在它挑我的程式碼，
也在它拒絕接受我在任務書裡塞給它的錯誤假設。

### 網路層——第二個模型的獨立審查

| # | 它說的 | 處置 |
|---|---|---|
| 1 | 只回傳 `hasMore`，把伺服器給的 `next` 連結丟掉，等於逼呼叫端自己算 offset——清單一變動就會重複或漏項 | **採納。**它自己選的「只能改一件事」。改成沿用伺服器的連結，並加測試證明第二次請求打的是伺服器給的那一個 |
| 2 | Ktor 的例外直接穿過資料層，UI 無法區分 error 與 offline | **採納但延後到 repository。**理由與替代方案寫進 `DECISIONS.md` 8：`Result` 的失敗仍然只是 `Throwable`，換位置不算解決 |
| 3 | 沒有 timeout，請求可能讓 UI 永遠停在 loading | **採納。**request／connect／socket 三個都設。它同時指出**retry 不能盲加**（會增加行動網路消耗）——這一點我同意，目前不做自動重試 |
| 4 | 測試可被假實作蒙混：`hasMore` 只驗 true／false、壞列只驗數量 | **採納。**改成斷言伺服器給的連結本身、後續請求的 query、存活文章的 id 與 title、被丟原因的內容 |
| 5 | 500 的測試只驗了例外類別，沒驗 status | **採納。**補上 `assertEquals(InternalServerError, thrown.response.status)` |
| 6 | `README.md` 還寫著「尚未實作任何功能」，與現況不符，評審一眼看得到 | **採納。**獨立一個 `docs:` commit 修正 |
| 7 | base URL 寫死在 companion object | **不採納（現在）。**單一公開 endpoint，注入設定現在只會增加樣板。記進 `.open-questions.md` |
| 8 | `HttpClient` 每次呼叫都新建、測試也沒 close | **接受但延後。**它的歸屬要等 Hilt 進場才有地方定義 |

第 1 項與上一輪的第 1 項是同一種錯誤：**我寫了一個「在靜止的清單上會通過」的實作**。
這兩次都不是程式碼寫錯，是我對世界的假設寫錯——而測試是照著同一個假設寫的，
所以它們也不會抓到。

另外一件值得記的：detekt 抓到我的 `catch (malformed: URLParserException) { return false }`
是 swallowed exception。改成不需要例外的前綴比對之後反而更嚴格——
它連 `api.spaceflightnewsapi.net.example.com` 這種相似 host 都擋得掉，而原本的 host 比對擋不掉。
**這一次是 lint 讓程式碼變好，不是讓它變醜。**

### repository——第二個模型的獨立審查

| # | 它說的 | 處置 |
|---|---|---|
| 1 | repository 把 mapper 帶回來的 `droppedReasons` 丟掉了：整頁壞掉會變成合法的空清單 | **採納。**`Loaded` 帶上 `dropped` 計數，並補一個「整頁都壞掉」的測試 |
| 2 | 沒預期到的例外會穿過 repository——「失敗在這裡停止被丟出」這句話有例外 | **採納。**加 `FeedFailure.Unexpected`，catch `Exception` 但先重新丟出 `CancellationException`；detekt 的兩條規則就地 suppress 並寫明理由 |
| 3 | 呼叫端可以自己造一個任意 `PageCursor`，repository 會原樣拿去打 | **採納。**傳進來的游標和回應裡的 `next` 走同一道檢查 |
| 4 | `Server(status)` 把 4xx 與 5xx 混在一起，429 的 `Retry-After` 也丟了 | **不採納（現在）。**目前四種狀態都對應同一個錯誤畫面；要分是在做重試政策的時候，那時才有行為需要它 |
| 5 | `IOException → Offline` 太寬（TLS、DNS、`ConnectException` 都不等於離線） | **不採納（現在）。**記進 `.open-questions.md`；改名或細分需要 UI 有對應的不同呈現才有意義 |
| 6 | `ArticleRepository` 只有一個 `suspend` 方法，撐不起「離線優先、快取先出」 | **接受它是對的，但順序上還沒到。**它自己也說目前的無狀態設計是較好的選擇；等快取進場時這個介面會改成 `Flow` |
| 7 | 用 MockEngine 丟 `java.net.SocketTimeoutException` 不等於真實的 OkHttp 逾時 | **接受這個限制。**它證明的是「這個例外到了 repository 會被判成 Timeout」，不是「OkHttp 會丟這個例外」。記進 `.open-questions.md` |
| 8 | **停止繼續堆資料層，先接一條垂直切片到畫面** | **採納。**這是它的「只能改一件事」。資料層到此為止，下一步是 DI 接線＋feed 畫面＋四種狀態 |
| 9 | `DECISIONS.md` 少了第 9 則、README 說「沒有 repository」已過期 | **採納。**同一個 commit 補上 |

第 8 項是這一輪最有價值的：**我問了它「現在該不該停下來」，它說該**。
一個沒有畫面的 take-home 不管資料層多乾淨，評審看到的都是一個空 app。
