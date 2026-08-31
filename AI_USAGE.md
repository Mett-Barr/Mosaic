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
