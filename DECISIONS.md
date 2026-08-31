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
