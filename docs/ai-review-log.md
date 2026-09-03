# AI 協作完整紀錄（附錄）

> 這是 [`AI_USAGE.md`](../AI_USAGE.md) 的附錄。作業要求 `AI_USAGE.md` 保持在**一頁**，
> 所以那份留給結論，逐輪的原始紀錄放這裡——包括每一次第二個模型的審查、
> 它說了什麼、我接受或否決以及理由。**沒有讀的必要**；它存在是為了讓上面那頁的
> 每一句話都可以被查證。

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

### 垂直切片（DI 接線＋畫面）——第二個模型的獨立審查

| # | 它說的 | 處置 |
|---|---|---|
| 1 | 無限捲動在下一頁失敗後**會自動再試**：`enabled` 沒有排除 `moreFailed`，effect 重啟時讀者還在底部 | **採納。**這是真的 bug，而且正好違反作業的「不浪費行動網路」。條件補上 `moreFailed == null` |
| 2 | ViewModel 忽略了資料層帶上來的 `dropped`：整頁壞資料會顯示成 Empty，最後一頁還會說「That is everything」 | **採納。**獨立一組紅→綠 commit。紅燈是 `expected an error, got Empty` |
| 3 | 這個 UI commit 沒有自動化測試；最小補法是 `androidTest` 的 `createComposeRule` | **接受但延後。**沒有裝置可跑，寫一個從未執行過的測試不算證據。列入 README 的延後表 |
| 4 | README 說「沒有 DI 接線、app 是空的」已經不成立 | **採納。**獨立 `docs:` commit |
| 5 | `DataModule.kt` 與 `FeedScreen.kt` 還是 untracked，只處理 tracked diff 會把接線漏掉 | **採納這個提醒。**commit 時逐一指定路徑 |
| 6 | `:core:ui` 沒有任何 source，但 app 與 feed 都宣告相依它，README 卻說它是設計系統 | **同意是問題，本輪延後。**兩條路（放進真的 theme／拿掉相依）都記進 `.open-questions.md` |
| 7 | Hilt／權限／網路／ProGuard 都沒有斷線；它實測打了 API 回 200 | **接受。**它自己去打了 API 驗證，這比讀程式碼有力 |

第 1 項是這一輪最重要的：**我寫的自動載入會在失敗後不斷重試**。
單元測試看不到它——那是 Compose effect 的行為——而它正好打中作業唯一明講「不要浪費」的東西。

### detail 那一段——第二個模型的獨立審查（**這一輪的程序是錯的**）

**先說程序**：這一段我為了趕進度，**先 commit 併回 `main` 才找它審**，
違反 `AGENTS.md` 迴圈的第 7 步（審查在提交之前）。它找到的問題因此變成後續的修正 commit
而不是提交前的修改。記在這裡是因為這正是 `AI_USAGE.md` 存在的理由——
規則沒守住的時候要看得出來。

第一次派工還中途死掉（`-o` 未落檔、殘留兩顆行程），事故記進本機的
`reference/codex-invocation-errors.md`；重派後正常。

| # | 它說的 | 處置 |
|---|---|---|
| 1 | `DetailViewModel` 落在 Activity 的 ViewModelStore：`Feed → A → B → 返回 A` 會看到 B 的內容，因為 `showing` 還停在 B | **採納。**它的「只能改一件事」。加 `rememberViewModelStoreNavEntryDecorator()`，每個 entry 有自己的 ViewModel |
| 2 | 沒有取消前一個請求：慢的 A 回來會蓋掉讀者正在看的 B | **採納。**先寫紅燈測試（`Expected no events but found Item(Content(...title=First...))`）再修 |
| 3 | 測試的假物件不看傳進去的 id，回傳固定 fixture 也能過 | **採納。**假物件改成按 id 回答，斷言改成比對整個 `ArticleItem` |
| 4 | 404 變成 `FeedFailure.Server(404)`，UI 再判斷 `status == 404`——HTTP 細節洩漏到畫面 | **同意，尚未修。**要加 `FeedFailure.Missing` 並改測試契約，列進 `.open-questions.md` |
| 5 | detail 沒有 TopAppBar，返回只在畫面底部；長文章要捲到底才看得到 | **同意，尚未修。**列進 `.open-questions.md` |
| 6 | 快速點擊會 push 兩次同一個 detail（缺 `dropUnlessResumed`） | **同意，尚未修。**列進 `.open-questions.md` |
| 7 | 下一個 must-have 應該是 **save/unsave＋離線閱讀**，再來 freshness，最後異質 feed | **接受它的順序。**理由是「先讓一條流程完整可靠，再擴張來源」，比我原本想的 freshness 優先更站得住腳 |

第 1 項是我自己完全沒想到的：**`hiltViewModel()` 在 `NavDisplay` 裡預設落在 Activity scope**。
單元測試看不到它，因為它根本不是 ViewModel 的邏輯問題，是接線問題。

### freshness 與異質 feed——過夜自動作業的一段

這兩個行為（`51ec060`、`126ae03`）是在人睡著時做的，流程與前面相同：
測試先紅、單獨 commit、實作到綠、`./gradlew build detekt lint` 全綠才提交。
**與規則不符的地方仍然是第 7 步**：第二個模型的審查排在合併之後，
因為沒有人可以在中間把關。這是同一個缺口第二次發生，記在這裡而不是解釋掉。

gate 在這兩輪抓到三件事，都是我自己沒看到的：

| gate | 抓到什麼 | 處置 |
|---|---|---|
| lint | `:core:data` 用了 `ConnectivityManager` 卻沒宣告 `ACCESS_NETWORK_STATE`——app 有宣告，但**用它的模組**沒有 | 在 `:core:data` 自己的 manifest 補上。權限該跟著提問的模組走 |
| detekt | `FeedScreen.kt` 到了 11 個函式，超過門檻 | 把天氣卡拆成 `WeatherCard.kt`。**這是對的抱怨**：它本來就是另一種東西 |
| detekt | `FileArticleCache` 吞掉了讀檔失敗的例外 | 把原因留在 `lastProblem`。快取讀不動不影響讀者，但「每次啟動都白付一次請求」值得被查得到 |

第一項特別值得記：**`:app` 宣告了權限，所以功能會動**——lint 抓的是
「這個模組單獨拿去用會壞」，那是模組化真正的成本，不是形式主義。

### freshness 與異質 feed——第二個模型的獨立審查（第七輪）

它抓到**兩個會壞掉的功能**，都是單元測試全綠、但單元測試自己的假設有問題：

| # | 它說的 | 處置 |
|---|---|---|
| 1 | **天氣卡會消失**：`loaded()` 主要那條路徑沒有帶 `weather`，所以天氣先回來、或成功載入下一頁，卡就不見了 | **採納。**它自己選的「只能改一件事」。真的是 bug，而且打中的正是第六個 must-have |
| 2 | **快取把 Error 變成 Empty**：`CachedArticles` 沒存 `dropped`，整頁壞資料下次啟動讀回來變成「空的 feed」 | **採納。**`dropped` 進快取格式，補跨 instance 測試 |
| 3 | 快取讀到「合法 JSON 但值不合法」（空白 id、壞時間戳）會炸，因為那不是 `SerializationException` | **採納。**多接一層 `IllegalArgumentException`，並補測試 |
| 4 | 寫檔失敗會讓已經成功的網路請求也失敗 | **採納。**寫入改成 best-effort |
| 5 | 快取保存了第一頁的 `next` 游標，接在新的一輪分頁上會重複／漏項 | **記下未修。**要真正解決需要 snapshot 語意或 keyset 游標，是設計層級的改動 |
| 6 | `article(id)` 穿透快取：離線點開「在快取第一頁裡但沒存過」的文章會失敗 | **記下未修。**它自己說這不違反狹義規格，但「看得到卻打不開」對使用者是壞的 |
| 7 | 我對「未來時間戳」的理由（時區、換日線）**不成立**——那些不改變 `Instant`，真正的成因是使用者改時間或系統校時 | **採納這個更正。**結論不變、理由要改，記在這裡 |
| 8 | 測試普遍只驗 id：mapper 的欄位映射、weather 的溫度／天空、`Cadence` 的實際數字都沒釘死 | **記下未修。**它列了八項具體補法 |

它對「這份作業最弱的一環」的判斷值得原文照抄：

> 這份作業最弱的一環是「垂直整合與可驗證性」：domain 與 data layer 的設計相當完整，
> 但 lifecycle、cache、非同步順序與實際畫面還沒有被同等強度地驗證。
> 現有 weather race 證明單元測試全綠，畫面仍可能錯。

**第 1 項的教訓**：那兩個假物件都 `yield()` 一次，於是「文章先完成」被固定下來，
race 從來沒有真的發生過。**兩個來源用同一種時序，等於只有一個來源。**

---

## 跑起來，然後被第二個模型抓到一個真的 bug

### 先跑，才看得到的兩件事

前七輪的 Codex 審查一直在說同一件事：**「垂直整合與可驗證性」是最弱的一環**。
這一輪把 app 裝上模擬器跑了一整趟（feed → 捲動 → detail → 存 → Saved → 關網路 →
force-stop → 重開），六個 must-have 在裝置上都成立，同時看到兩件單元測試不會說的事：

| 跑起來看到的 | 處置 |
|---|---|
| **天氣卡在離線重開後整個不見**。讀數只存在記憶體 | **修了。**寫進檔案。我原本的註解說「上次開 app 的溫度不值得顯示」——那句話回答的是「值不值得顯示」，而那個問題 freshness 政策已經有答案了 |
| **日期是 `31 8月, 21:14`**。英文介面配中文月份 | **修了。**`ofPattern` 沒給 locale，java.time 就去問裝置。順手把兩份重複的 formatter 收進 `:core:ui` |

**教訓**：這兩個都不是「測試沒覆蓋到」，是**測試問不出來的問題**。
一個要程序真的被殺，一個要裝置真的是中文的。

### 第二個模型的獨立審查（第八輪）

| # | 它說的 | 處置 |
|---|---|---|
| 1 | **`Instant.parse` 丟的是 `DateTimeParseException`，那是 `DateTimeException`，不是 `IllegalArgumentException`**——三個檔案存放器的防護全部漏接 | **採納，這是真的 blocker。**六個測試寫下去全紅。**而且這正是第七輪第 3 項那個修正沒修乾淨的地方**：當時我補了 `IllegalArgumentException` 就以為接完了 |
| 2 | `SerializationException` 本身**就是** `IllegalArgumentException` 的子類，我把一般的排在前面，所以那個 branch 從來沒執行過，註解還寫著相反的話 | **採納。**順序排對 |
| 3 | 寫檔不是原子的：`writeText` 先清空再寫，程序在中間被殺會留下半個檔案 | **採納。**三個存放器都改成寫暫存檔再 rename。閱讀清單另外改成寫失敗時回報而不是丟例外，且記憶體不再跑在檔案前面 |
| 4 | `DECISIONS.md` 16 把 `published_at_lte` 的保證**講得太滿**——那是時間截點，不是伺服器 snapshot；時鐘偏差與回填仍然擋不住 | **採納這個更正。**改寫成「大幅降低而非消除」，並列出它擋不住的四種情況。去重那一層因此留著 |
| 5 | 天氣檔放在 `filesDir`，但它是可重新取得的快取，該放 `cacheDir`（文章就是） | **採納。**一行，理由寫在 DI 旁邊 |
| 6 | freshness 量的是 `askedAt`（多久不再問），不是資料本身多舊；Open-Meteo 的即時值來自每 15 分鐘一步的模式 | **採納這個澄清。**寫進 `Freshness` 的 KDoc 與 README——政策管得住的是發問頻率 |
| 7 | 快取的舊游標**是對的**：它屬於那一頁的視窗，換成新視窗反而自我矛盾 | **接受這個反駁。**我原本把它列在待辦上，現在寫進 `DECISIONS.md` 16 當成刻意選擇 |
| 8 | 「快取用裝飾器、天氣用建構子參數」的不對稱，我的理由**站不住腳**——來源數量不決定該不該用裝飾器 | **接受批評，維持現狀。**它自己也說這在 take-home 尺度是合理簡化，歸類為意見而非缺陷。理由改寫，不再宣稱那是原則問題 |
| 9 | predictive back 那條**根本不是問題**：manifest 預設就是開的，且本專案 target SDK 37 | **接受更正。**那個 commit 修的是明確宣告與那行警告，不是行為缺陷——commit 訊息當時講得比實情大 |
| 10 | 圖片 `contentDescription = null` 在圖只是重複標題時**是對的** | **接受。**不再把它當成無條件的缺失 |

**這一輪最值得記的**：第 1 項是它直接指著我寫的 catch 問「你確定 `Instant.parse`
丟的是這個嗎」。我寫了三次同一個錯，因為第二、三次是照第一次複製的。
**同一個模型再讀一次會再確認一次自己的假設**——換一個模型才會去查。

---

## 轉場的三個缺陷——這一輪沒有第二意見

第 58、59、60 則那三個 commit（bar 不再滑、縮圖回程的圓角、標題的那一層淡）
**沒有經過 Codex 的獨立審查**。不是跳過，是它連續四次沒有產出：

| 次 | 派工方式 | 結果 |
|---|---|---|
| 1 | 31KB 任務書走位置參數 | `Argument list too long`，codex 根本沒被啟動 |
| 2 | 改成短 argv 指向任務檔 | 有啟動、讀完 diff 與三個檔，18 分鐘後 log 停止成長、`-o` 從未落檔，CPU 2.7 秒 |

（前一天同一個 repo 的兩次也是同樣結果，記在本機的調用事故檔裡。）

依 `AGENTS.md`，Codex 的意見是**建議而非閘門**，所以它缺席不擋 commit——
但缺席要寫下來，否則 history 會讓人以為第 7 步跑過了。

**替代做法**：這三個 commit 的證據全部改成**裝置上的逐幀錄影**，
而且每一個結論都附了可核對的幀號（`animator_duration_scale=10`，
`screenrecord` 後 `ffmpeg -fps_mode passthrough` 抽出實際被捕捉的幀）。
標題那一則甚至是把兩個候選各建一次、各錄一次，看幀選的——
**這比一份文字審查更接近「有人反對過」**，因為畫面不會替我圓場。
它換不掉的是 Codex 唯一的價值來源：另一個實驗室的盲區。三則的「取捨與限制」
裡各自寫了它們現在依賴而沒有東西在檢查的事。
