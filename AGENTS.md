# 工作約定

這份文件描述本專案**實際在用**的開發流程與 AI 協作規則。它不是理想，是把做法寫下來——
`git log` 上的每個 commit 都是照這裡跑出來的。

單人開發，全程與 AI coding agent 協作。流程對應三個既有實踐：
[spec-driven development](https://github.github.com/spec-kit/)（人定義驗收條件）、
[red/green TDD](https://simonwillison.net/guides/agentic-engineering-patterns/red-green-tdd/)（測試先行且必須先看它失敗）、
human-in-the-loop（人審測試與 diff）。

---

## 常用指令

```bash
./gradlew build detekt lint          # 完整 gate，合併前必跑
./gradlew :app:assembleDebug         # 建置
./gradlew testDebugUnitTest          # 全部 JVM 單元測試
./gradlew :feature:feed:testDebugUnitTest   # 單一模組
./gradlew connectedDebugAndroidTest  # instrumentation 測試（需裝置）
./gradlew createModuleGraph          # 重新產生 README 的模組相依圖
```

`allWarningsAsErrors = true`，編譯警告會直接讓建置失敗。

---

## 分工邊界

| 事項 | 誰做 |
|---|---|
| **定義行為與驗收條件** | **人** |
| 寫測試 | AI |
| **審測試** | **人** |
| 寫實作 | AI |
| **審 diff：接受／拒絕／重寫** | **人** |
| 跑 gate | AI 執行，人複核輸出 |
| **架構取捨** | **人**（記入 `DECISIONS.md`） |

**AI 負責產出，人負責定義「對」與驗收。**

### 審測試審什麼

主流的 TDD-with-agents 建議是「人寫測試、AI 寫實作」，本專案改為「AI 寫、人審」——
動手寫誰都可以，會出事的是**驗收條件也交給 AI 定**。作為補償，人審測試時明確檢查三件事：

1. 它**先失敗過**嗎？（沒看過紅燈的測試不算測試）
2. 測的是**行為**還是實作細節？
3. assertion 有沒有可能被 hardcode 的回傳值蒙混過去？

---

## 開發迴圈

### commit loop——產出一個 commit

```
1  [人]    定義行為 + 驗收條件
2  [AI]    寫測試，確認它失敗
3  [人]    審測試（見上）
4  [AI]    實作到測試綠
5  [gate]  ./gradlew build detekt lint 全綠
6  [人]    審 diff → 接受 / 拒絕 / 重寫，理由當下記進 AI_USAGE.md
7  [AI]    commit
8  [人]    有架構取捨 → 同一個 commit 內寫 DECISIONS.md
```

第 1 與第 3 步是這個迴圈的支點。其餘步驟全部由 AI 完成也無妨，只要這兩步在人手上。

第 8 步必須當下寫：「當時考慮過哪些替代方案」事後回想會失真，沒選的那條路兩天後
想不起來為什麼不選。

### session loop——一個工作階段

```
A  從 backlog 取一批相關行為
B  有未決的架構取捨 → 先 spike 或直接決定並寫 DECISIONS.md
C  跑 N 次 commit loop
D  回顧：更新 backlog、檢查文件有沒有落後於程式碼
```

`spike/` 分支的產出預期是**知識**而非程式碼，驗證完直接丟棄，不併回。

> 這裡的 commit / session 不是 DevEx 領域慣用的 inner / outer loop
> （那組詞指的是「本機 code-build-test」與「push 之後的 CI/CD」），刻意改名以免混淆。

---

## 驗證 gate

**Agent 自述完成不構成證據。唯一的完成定義是本地實跑 `./gradlew build detekt lint` 全綠。**

這不是多疑。Agent 回報改檔成功但檔案未落地是跨廠商的公開問題
（[anthropics/claude-code#4462](https://github.com/anthropics/claude-code/issues/4462)、
microsoft/vscode-copilot-release#13062）。Anthropic 官方把這個 failure pattern 命名為
**trust-then-verify gap**，對策是 *"Always provide verification. If you can't verify it, don't ship it."*

**這道 gate 目前靠人執行，不是 hook 強制的。** 這份文件是 advisory 而非 enforcement——
CI 在 push 與 PR 跑同一道 gate，那才是唯一被機器保證的一層。單人專案不值得為此加
pre-commit hook，但這個限制要講清楚，不要讓讀者以為它是自動的。

---

## 禁止事項

1. **不得為了讓 gate 變綠而修改或刪除測試。** 實作階段的 diff 若動到測試檔，必須另外過人審。
2. **不得修改 `gradle/libs.versions.toml` 的版本**。依賴升級是獨立的 `build(deps)` commit。
3. **不得修改 `.github/workflows/`**。
4. **不得 squash。** history 是本專案的交付物之一。
5. **未經人審的產出不得進入 commit。**
   （對應 Willison 列的唯一明確 anti-pattern：*"Don't file pull requests with code you haven't reviewed yourself."*）

---

## Commit 與分支

沿用 [`docs/git-conventions.md`](docs/git-conventions.md)：Conventional Commits、subject 寫
**為什麼**而不只是**什麼**、一個 commit 一個行為、短命主題分支 `--no-ff` 併回 `main`。

AI 參與產出的 commit 加上 trailer：

```
Assisted-by: LLM Claude Code
```

採 [Linux kernel 的 `Assisted-by:` 格式](https://docs.kernel.org/process/coding-assistants.html)
而非 `Co-authored-by:`——多數專案不把 AI 列為 co-author，
[apache/airflow 的 AGENTS.md](https://raw.githubusercontent.com/apache/airflow/main/AGENTS.md)
明文禁止。揭露寫進 history 而不只寫在文件裡，是因為 history 本身就是證據。

---

## 文件配置

| 檔案 | 性質 | 內容 |
|---|---|---|
| `AGENTS.md` | 前瞻·規則 | 本檔 |
| `CLAUDE.md` | — | 只有一行 `@AGENTS.md`。Claude Code 讀 `CLAUDE.md` 而非 `AGENTS.md`（[官方文件](https://code.claude.com/docs/en/memory)），需要 import 橋接 |
| `DECISIONS.md` | 當下·取捨 | 輕量版 [ADR](https://adr.github.io/)：選了什麼·考慮過什麼·取捨是什麼 |
| `AI_USAGE.md` | 回顧·紀錄 | 實際發生了什麼：接受／拒絕／重寫，以及被 gate 抓到的錯 |

---

## 這份文件本身

規則與實際做法不符時，**改文件或改做法，不要讓兩者並存**。
一份寫了卻沒在跑的規則比沒有規則更糟——它讓讀者無法判斷 history 裡哪些是真的。

**篇幅上限 200 行。** 依據是
[Claude Code 官方建議](https://code.claude.com/docs/en/best-practices)
（*"target under 200 lines"*）與 GitHub Copilot 的 *"no longer than 2 pages"*；
更關鍵的是 [arXiv:2602.11988](https://arxiv.org/abs/2602.11988) 實測發現 context file
**平均不提升任務成功率，卻讓推理成本增加 20% 以上**——agent 是忠實遵守而非忽略這些
指令，多餘的內容讓它擴大探索。新增一條規則前先問：**拿掉它會讓 agent 犯錯嗎？** 不會就別加。
