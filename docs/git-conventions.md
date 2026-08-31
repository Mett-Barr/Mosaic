# Git 慣例

這份文件描述本專案實際在用的分支與提交慣例。它是把既有做法寫下來，不是新規定——
從它被寫下的那個 commit 起，後面每一個 commit 都符合這裡寫的規則。

> 它之前的骨架 commit 是直線的，沒有 `--no-ff` merge：那時還沒有主題分支可以分。
> 規則不追溯適用於建立規則之前的 history。

---

## 提交訊息：Conventional Commits

```
<type>(<scope>)<!>: <subject>
```

- **type** 必填，小寫，見下表
- **scope** 選填，指模組或功能區（`feed`、`saved`、`network`、`deps`）
- **`!`** 標記破壞性變更，放在冒號前
- **subject** 祈使句、小寫開頭、不加句號

### type

| type | 用於 |
|---|---|
| `feat` | 新增行為 |
| `fix` | 修正缺陷 |
| `refactor` | 不改變行為的結構調整 |
| `test` | 只動測試 |
| `docs` | 只動文件 |
| `build` | 建置設定、依賴 |
| `ci` | CI 設定 |
| `chore` | 其他雜項 |

### subject 寫「為什麼」，而不只是「什麼」

```
❌ refactor: change FeedViewModel
✅ refactor(feed)!: derive fetching from demand and staleness
```

第二個讓人不必打開 diff 就知道發生了什麼概念上的改變。

> 訊息本身維持英文。文件用中文是給讀者看的，commit 訊息則跟著 Conventional Commits
> 的既有詞彙走——`feat`／`fix`／`refactor` 這些 type 本來就是英文，subject 換成中文會
> 讓同一行裡兩種語言互相打架。

### AI 參與產出的 commit 加上 trailer

```
Assisted-by: LLM Claude Code
```

格式取自 [Linux kernel](https://docs.kernel.org/process/coding-assistants.html)。
刻意不用 `Co-authored-by:`——多數專案不把模型列為作者，
[apache/airflow](https://raw.githubusercontent.com/apache/airflow/main/AGENTS.md)
更是明文禁止。作者身分伴隨責任，而責任留在人身上。

審查者不寫進 trailer。第二個模型的審查意見記在 `AI_USAGE.md`——**審查不構成作者身分**。

---

## 分支模型

短命的主題分支，`--no-ff` 併回 `main`。沒有 `develop` 分支。

```
main  ──●────────────●────────────●──
         \          / \          /
          ●──●──●──●   ●──●──●──●
       feat/article-feed   feat/offline-saved
```

### 分支命名：與 commit type 同一套詞彙

```
<type>/<短描述>
```

| 分支 | 它產出的 commit |
|---|---|
| `feat/article-feed` | `feat(feed): show articles in a list` |
| `refactor/feed-item-model` | `refactor(domain)!: let the feed hold items of different shapes` |
| `docs/readme` | `docs: explain the freshness reasoning` |

前綴共用讓分支名與它產出的 commit 互相印證，回頭讀 history 時不必猜這個分支當初
在做什麼。

| 前綴 | 用於 | 生命週期 |
|---|---|---|
| `spike/` | 探索、原型、設計比較 | **可能不會被併回**——問題答完就丟 |

`spike/` 刻意與其他前綴區隔：它的產出預期是**知識**而不是程式碼。把它排除在 `feat/`
之外，是為了不讓拋棄式的工作污染 history。

### 為什麼用 `--no-ff`

fast-forward 會把主題分支的存在抹掉，history 變成一條直線，看不出哪幾個 commit 屬於
同一件事。`--no-ff` 保留 merge commit，`git log --graph` 上一眼就能看出每個主題的範圍。

```bash
git merge --no-ff feat/article-feed
```

---

## 一個 commit 一件事

每個 commit 都應該能獨立審查、獨立 revert。

某個變更的**後果**應該獨立成一個 commit，而不是併進造成它的那個變更裡——分開才能讓
history 說出「因為抓取改成推導式，這個類別就沒有存在理由了」這件事。

---

## 合併前的檢查

`main` 的**第一父系**（`git log --first-parent`）每個 commit 都應該可建置、測試全綠。
topic branch 內部的 `test:` commit 是刻意留紅的——理由見
[`AGENTS.md`](../AGENTS.md) 的「紅燈要留下證據」。本地跑：

```bash
./gradlew build detekt lint
```

專案設定 `allWarningsAsErrors = true`，Kotlin 編譯器警告會直接讓建置失敗（範圍見 [`AGENTS.md`](../AGENTS.md)）。
CI 在推送與 PR 時跑同一道 gate——為什麼是 CI 而不是這份文件在把關，見
[`AGENTS.md`](../AGENTS.md)。
