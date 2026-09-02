# 這個 codebase 靠什麼證明自己

一份調查與其結論。分成三段：**已經在跑的**、**調查後刻意不做的**、**做不到的**。

寫這份文件的理由跟 `AGENTS.md` 那條 gate 是同一個：
*"Always provide verification. If you can't verify it, don't ship it."*
差別在於 gate 講的是「一次變更」，這份講的是「整個 codebase」。

---

## 一、已經在跑的

每一項都掛在 `./gradlew build detekt lint` 這一道上，CI 在 push 與 PR 跑同一道。
**沒有任何一項有 baseline 檔**——baseline 的作用是讓既有問題不算數，那正好是這份
文件想避免的那種「看起來很乾淨」。

| 工具 | 管什麼 | 為什麼是它 |
|---|---|---|
| **Kotlin `allWarningsAsErrors`** | 編譯器警告 | 免費，而且是唯一有型別資訊的那一層 |
| **Android Lint** | 平台正確性、資源、API 等級 | 官方，AGP 內建 |
| **[Slack compose-lints](https://slackhq.github.io/compose-lints/) 1.4.2** | Compose 慣例 | Android 官方 lint **幾乎不檢查 Compose**；這是生態系收斂到的那一套 |
| **[detekt](https://detekt.dev) 1.23.8** | Kotlin 程式碼氣味、複雜度 | `buildUponDefaultConfig`、`maxIssues: 0`、無 baseline |
| **[Kover](https://github.com/Kotlin/kotlinx-kover) 0.9.9** | 測試覆蓋率 | JetBrains 官方，Kotlin 專用（JaCoCo 不理解 inline 與 `suspend` 的位元組碼） |
| **`checkModuleDependencies`**（自寫） | 模組相依規則 | 見下方「為什麼自己寫」 |
| **`createModuleGraph` + CI 檢查** | README 的模組圖 | 文件與程式碼不一致時 CI 會紅 |

### 為什麼模組規則是自己寫的

市面上的做法是 [Konsist](https://docs.konsist.lemonappdev.com/) 或 ArchUnit，兩者都是
**測試**：寫一個 test 去斷言架構。這個專案改成 **build 階段的 task**，理由是
`AGENTS.md` 禁止事項第 1 條——測試可以被改，而改測試讓 gate 變綠是最容易發生的那種
自我欺騙。相依規則寫在 `build.gradle.kts` 裡，動它就是動建置腳本，在 diff 上藏不住。

Gradle 本來就知道真實的模組邊界，不需要另一個工具重新推導一次。

---

## 二、調查過，刻意不做

**不做也是一個決定，而且比默默不做誠實。**

### ktlint／detekt-formatting

**不做的理由：這個專案沒有第二個人。** ktlint 解決的是「多人對縮排與 import 順序的
意見不一致」，而 Kotlin 官方 code style 已經由 `kotlin.code.style=official` 加上
IDE 格式化涵蓋。在單人專案裡它增加的是一組會擋建置的規則，換到的是一個不存在的問題。

如果這份 codebase 交給團隊接手，這是**第一個該加的**。

### Qodana / SonarQube

**不做的理由：它們的價值在趨勢，不在單次掃描。** 兩者都是伺服器端儀表板，衡量的是
「這個 quarter 的技術債往哪走」。一個三天的專案沒有趨勢可看，而單次掃描的結果與
detekt ＋ lint 高度重疊。

### Konsist / ArchUnit

見上方「為什麼自己寫」——已經用更難繞過的方式做了同一件事。

### Baseline Profile / Macrobenchmark

**不做的理由：沒有可信的測量環境。** Baseline Profile 要在真機上錄製才有意義，
模擬器數字不能拿來聲稱啟動變快。**與其給一個測不準的數字，不如不給。**

### binary-compatibility-validator

**不做的理由：這不是函式庫。** 它防的是「公開 API 意外變更破壞下游」，這個專案沒有下游。

---

## 三、做不到的（不是不想做）

| 想做 | 為什麼做不到 |
|---|---|
| **CodeQL / Dependabot** | `AGENTS.md` 禁止事項第 3 條：不得修改 `.github/workflows/` |
| **螢幕截圖測試** | 專案目前**一個 `@Preview` 都沒有**。Google 官方的 Compose Preview Screenshot Testing 以 preview 為輸入，要先有 preview |
| **相依套件升級** | 禁止事項第 2 條：版本升級必須是獨立的 `build(deps)` commit，不能混進其他工作 |

前兩項不是取捨，是**這一版真的還沒做**。第三項是流程限制，不是能力限制。

---

## 三點五、覆蓋率實際上長什麼樣

`./gradlew koverHtmlReport` 產生。生成碼（Hilt、Room 的 `_Impl`、Compose 的
`ComposableSingletons`）已排除——那不是這個專案寫的程式碼，算進去衡量的是註解處理器。
**`@Composable` 刻意沒有排除**，理由在下一節。

```
moozy/mosaic/data/article/network   100.0%   71/71
moozy/mosaic/domain/model            98.1%   53/54
moozy/mosaic/data/saved              97.2%  106/109
moozy/mosaic/data/article            95.0%   38/40
moozy/mosaic/data/weather            92.1%  140/152
──────────────────────────────────────────────────
moozy/mosaic/feature/detail          31.3%   67/214
moozy/mosaic/feature/feed            23.0%   84/366
moozy/mosaic/feature/saved           15.3%   18/118
moozy/mosaic/core/ui                  2.9%    3/103
moozy/mosaic/navigation               0.0%    0/111
moozy/mosaic/data/di                  0.0%    0/60
moozy/mosaic (MainActivity)           0.0%    0/5
──────────────────────────────────────────────────
全專案                                41.3%  580/1403
```

**這個數字剛從 46.2% 掉到 41.3%，而那是一次改善造成的。** 被測到的行數一行沒少
（580 → 580）；分母變大了，因為新增了每個畫面狀態的 `@Preview`（feed 從 292 行變
366 行）和一個新的 `:navigation` 模組（111 行）。preview 是給人看的，測試不會執行它。

**這正是不設門檻的理由。** 一條「不得低於 46%」的規則會擋下這兩件事——而它們都讓
這份 codebase 變好了。

**46.2% 這個數字單獨拿出來會誤導。** 它描述的不是「測試寫得不夠」，而是一條很清楚的
分界線：**所有做決定的地方在 92–100%，沒測的全部是畫面與接線**。

那條線是刻意的，不是力有未逮：畫面的取捨記在 `DECISIONS.md` 20，接線（`data/di`）
測的會是 Hilt 而不是這個專案。

**沒有設覆蓋率門檻。** 一個「必須往上」的數字，會讓人開始為數字寫測試而不是為行為寫
測試——那跟 `AGENTS.md` 禁止事項第 1 條是同一個原則。Kover 在這裡是**量尺不是閘門**。

---

## 四、這份 codebase 最弱的一環

**畫面沒有自動化測試。** 126 個測試全部在 ViewModel 與其下，`@Composable` 一個都沒被
測過。所以「畫面畫對了嗎」目前的答案只有「有人用眼睛看過模擬器」。

這件事在 `DECISIONS.md` 20 有記錄，而且是**知情的取捨**而非疏漏：Compose 的測試需要
instrumentation 或 Robolectric，兩者都比它們在這個規模上防住的問題貴。

**但它仍然是最弱的一環**，而承認這件事比在文件裡繞過它有價值。真的要補，路徑是：
先為每個畫面寫 `@Preview`（那本來就該有），再套 Google 官方的
[Compose Preview Screenshot Testing](https://developer.android.com/studio/preview/compose-screenshot-testing)——
它以 preview 為輸入，所以第一步的成本本來就要付。

---

## 五、AI 時代的那一層

這個專案本身就是「AI 協作」的樣本，所以品質做法也包含**對 AI 產出的驗證**：

| 做法 | 在哪裡 |
|---|---|
| **第二個模型獨立審查** | `AGENTS.md`「兩個模型，不同角色」——Codex 讀 diff，任務是提出反對意見 |
| **不同實驗室的模型** | 同一個模型的第二意見會複製它自己的盲區 |
| **agent 自述不算完成** | `AGENTS.md`「驗證 gate」——唯一的完成定義是本地實跑全綠 |
| **紅燈留在 history 裡** | 測試與實作分成兩個 commit，紅燈那個的 body 貼真實失敗輸出 |
| **審查不構成作者身分** | Codex 不進 commit trailer；`Assisted-by:` 只記誰產出 |

**這一層才是這個專案真正不常見的地方。** 靜態分析工具大家都會裝；
「怎麼知道 AI 說它做完了是真的」目前還沒有標準答案，而
[anthropics/claude-code#4462](https://github.com/anthropics/claude-code/issues/4462)
說明它是跨廠商的真實問題。

### 一個實際的例子

加 Slack compose-lints 之後報告是空的。**空報告有兩種原因**：規則沒載入，或真的沒有違規。
兩者在報告上長得一模一樣。所以在 commit 之前故意寫了一個違規的 composable：

```
Error: Parameters in a composable function should be ordered following this pattern:
params without defaults, modifiers, params with defaults ...
[ComposeParameterOrder from com.slack.lint.compose:compose-lints]
```

確認規則會叫，才把探針刪掉。**這一步不做的話，「Compose 零違規」是一句沒有根據的話。**
