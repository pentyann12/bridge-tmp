# CORBA-REST ブリッジジェネレーター — 動作機序ドキュメント

## 概要

このツールは CORBA サービスの IDL ファイルを入力として受け取り、その CORBA サービスを REST API として公開する Spring Boot プロジェクトを自動生成します。

```
IDL ファイル
    │
    ▼
Phase 1: idlj でJavaスタブ生成
    │
    ▼
Phase 2: グローバルスコープスタブの後処理（パッケージ移動）
    │
    ▼
Phase 3: Spring Boot プロジェクトの生成
    │
    ▼
生成された Spring Boot プロジェクト
```

処理は2つのクラスに分かれています。

- **`Main.java`** — CLI エントリポイント。引数の解析・idlj 実行・スタブ後処理・サービス検出を担当
- **`SpringBootProjectGenerator.java`** — スタブ情報を元に Spring Boot プロジェクト一式を生成する

---

## Main.java の動作フロー

### エントリポイント: `main(String[] args)`

`gen-spring` コマンドを受け取ってから終了するまでの全体フローを制御します。

```
main()
 ├─ 引数パース（--stub-root, --output-root, --base-package 等）
 │
 ├─ IDL ファイルごとに:
 │    ├─ collectIdlPackagePrefixTargets(idlFile)  → pkgPrefix ターゲット収集
 │    └─ runIdlj(idljJar, idlFile, stubRoot, ...)  → Javaスタブ生成
 │
 ├─ relocateDefaultPackageStubs(stubRoot, idlPackagePrefix)  → スタブ後処理
 │
 ├─ collectServiceInterfaces(stubRoot)  → *Operations インターフェースを収集
 │
 └─ (--single-project)
       ├─ true:  SpringBootProjectGenerator.generateAll(services, ...)
       └─ false: サービスごとに SpringBootProjectGenerator.generate(service, ...)
```

---

### Phase 1: Javaスタブ生成

#### `collectIdlPackagePrefixTargets(idlFile)` → `List<String>`

IDL ファイルおよびそれが `#include` で参照するファイルを再帰的に解析し、`idlj -pkgPrefix` に渡すターゲット名を収集します。

1. `collectIncludedFiles(file, collected)` を呼び出して対象 IDL ファイルの推移的閉包を構築する
2. 全ファイルから `module <name>` を正規表現で抽出する
3. module が見つからない場合のフォールバック: `interface` / `struct` / `union` / `enum` / `exception` の型名を収集する

#### `collectIncludedFiles(file, collected)`

`#include "..."` を再帰的に辿り、参照されるすべての IDL ファイルを `collected` に追加します。循環参照・存在しないファイルは安全にスキップします。

#### `runIdlj(idljJar, idlFile, stubRoot, idlPackagePrefix, packageTargets)`

idlj をサブプロセスとして実行してJavaスタブを生成します。

- `stubRoot/idlj-args/<basename>.args` に argfile を書き出し、`java @argfile` で idlj を起動する（スペースを含むパスの問題を回避）
- `-emitAll`（すべての型を出力）、`-fclient`（クライアント側のみ生成）を常に指定する
- `idlPackagePrefix` が指定されている場合、`packageTargets` の各名前に対して `-pkgPrefix <name> <prefix>` を追加する

---

### Phase 2: グローバルスコープスタブの後処理

#### `relocateDefaultPackageStubs(stubRoot, idlPackagePrefix)`

IDL のグローバルスコープ（モジュール外）で定義された型は、idlj がデフォルトパッケージ（`stubRoot` 直下）に `.java` を出力します。このメソッドはそれらを名前付きパッケージへ移動し、全スタブの import 文を整合させます。

移動先パッケージの決定は `resolveGlobalStubPackage()` に委譲します。

処理は3つのパスに分かれます:

**Pass 1** — import 文の書き換え  
`stubRoot` 以下の全 `.java` ファイルをスキャンし、移動したクラスへの `import ClassName;`（ドットなし）を書き換えます。
- 移動先パッケージ内のファイル: 同一パッケージになるため import 行を**削除**
- 移動先パッケージ外のファイル: `import <targetPackage>.ClassName;` に**書き換え**

**Pass 2** — import の追記  
idlj はデフォルトパッケージのクラスを「同一パッケージ」として扱うため、import 文なしで参照しているファイルがあります。Pass 1 後に再度スキャンし、クラス名を参照しているにも関わらず import がないファイルへ `insertImport()` で import を追加します。

**Pass 3** — bare import の削除  
`import Xxx;`（ドットなし）形式の import を全スタブから削除します。リロケーション後はデフォルトパッケージに何も存在しないため、残留する bare import はすべてコンパイルエラーの原因になります。

#### `resolveGlobalStubPackage(stubRoot, idlPackagePrefix, excludeFiles)` → `String`

グローバルスタブの移動先パッケージを次の優先順位で決定します:

1. `idlPackagePrefix` が空でなければそれを使用
2. 既存スタブの `package` 宣言を収集し、`packageCommonPrefix()` で共通プレフィックスを求める
3. 共通プレフィックスが得られなければ `"generated"` を使用

#### `insertImport(content, importStatement)` → `String`

`package` 宣言行の直後に import 文を挿入するヘルパーです。

#### `packageCommonPrefix(a, b)` → `String`

`"com.example.a"` と `"com.example.b"` → `"com.example"` のように、ドット区切りパッケージ名の共通プレフィックスを返します。

---

### Phase 3: サービス検出

#### `collectServiceInterfaces(stubRoot)` → `List<ClassOrInterfaceDeclaration>`

`stubRoot` 以下の全 `.java` ファイルを JavaParser でパースし、名前が `*Operations` で終わるインターフェース宣言を収集します。これが CORBA サービスインターフェースの識別子になります。

---

## SpringBootProjectGenerator.java の動作フロー

### Public API

#### `generate(serviceIface, stubSourceRoot, outputDir, basePkgName, returnAsDto)`

単一サービスを1プロジェクトに生成します。

```
generate()
 ├─ setupProject()                  → プロジェクト共通部分の生成
 ├─ generateService(..., "CorbaClient")  → サービス固有ファイルの生成
 └─ writeApplicationProperties()   → application.yml の出力
```

#### `generateAll(services, stubSourceRoot, outputDir, basePkgName, returnAsDto)`

複数サービスを1プロジェクトにまとめます。

```
generateAll()
 ├─ setupProject()                  → プロジェクト共通部分（1回だけ実行）
 ├─ サービスごとに:
 │    └─ generateService(..., serviceName+"CorbaClient")
 └─ writeApplicationProperties()   → 全サービス分の application.yml
```

---

### プロジェクト共通部分の生成

#### `setupProject(stubSourceRoot, outputDir, basePkgName)` → `ProjectLayout`

ディレクトリの作成と、全サービスで共有されるファイル群を出力します。

```
setupProject()
 ├─ ディレクトリ作成:
 │    controller/, dto/, mapper/, corba/, resources/
 │
 ├─ writeBuildGradle()          → build.gradle
 ├─ writeApplicationClass()     → Application.java (@SpringBootApplication)
 ├─ writeServletClass()         → ServletInitializer.java
 ├─ copyIdlStubSources()        → スタブをプロジェクトへコピー
 ├─ writeAnyValueDto()          → dto/AnyValue.java
 ├─ writeAnyMapper()            → mapper/AnyMapper.java
 │
 ├─ collectStructClasses()      → 構造体クラスの収集
 └─ 構造体ごとに:
      ├─ DtoGenerator.generate()    → XxxDto.java
      └─ MapperGenerator.generate() → XxxMapper.java
```

返値の `ProjectLayout` は `javaDir`, `controllerDir`, `corbaDir`, `stubSourceRoot` を保持し、後続の `generateService()` に渡されます。

---

### サービス固有ファイルの生成

#### `generateService(serviceIface, serviceName, layout, basePkgName, returnAsDto, clientClassName)`

1つのサービスに対応するファイル群を生成します。

```
generateService()
 ├─ サービスパッケージを取得（JavaParser の CompilationUnit から）
 ├─ メソッドごとに:
 │    ├─ generateRequestDto()    → XxxRequestDto.java（in引数のみフィールド化）
 │    └─ (out引数があれば)
 │         └─ generateResponseDto() → XxxResponseDto.java
 │
 ├─ writeCorbaClient()           → corba/XxxCorbaClient.java
 └─ writeController()            → controller/XxxController.java
```

---

### ファイル生成の詳細

#### `writeBuildGradle(outputDir)`

Spring Boot 4.0.6、glassfish-corba-orb 4.2.5、springdoc-openapi 等の依存を含む `build.gradle` を出力します。

#### `writeApplicationClass(basePkgName, pkgBaseDir)`

`@SpringBootApplication` アノテーション付きの `Application.java` を生成します。gmbal の警告を抑制するシステムプロパティ設定も含みます。

#### `writeServletClass(basePkgName, pkgBaseDir)`

WAR デプロイ用の `ServletInitializer.java` を生成します。

#### `copyIdlStubSources(stubSourceRoot, javaDir)`

idlj が生成したスタブファイルを、相対パスを維持してプロジェクトの `src/main/java/` へコピーします。

#### `writeAnyValueDto(dtoDir, basePkgName)`

`org.omg.CORBA.Any` の代替として JSON でやり取りするための DTO クラス `AnyValue`（`type: String`, `value: Object`）を出力します。

#### `writeAnyMapper(mapperDir, basePkgName)`

`org.omg.CORBA.Any` ↔ `AnyValue` を相互変換するユーティリティクラス `AnyMapper` を出力します。

- `toAny(AnyValue dto)`: `dto.type` の文字列に従って適切な `any.insert_xxx()` を呼び出す
- `toAnyValue(Any any)`: `TCKind` に従って `any.extract_xxx()` を呼び出す。`tk_null` / `tk_void` の場合は `null` を返す（空 Any でのエラーを防ぐ）

#### `writeCorbaClient(serviceName, servicePackage, methods, corbaDir, basePackage, clientClassName)`

CORBA サービス呼び出しをラップする Spring Bean を生成します。

```java
@Component
public class XxxCorbaClient {
    private final XxxService service;

    // ORB 初期化はコンストラクター（アプリ起動時）に1回だけ実行される
    public XxxCorbaClient(@Value("${corba.XxxService.ior}") String ior) {
        ORB orb = ORB.init(new String[0], null);
        org.omg.CORBA.Object obj = orb.string_to_object(ior);
        this.service = XxxServiceHelper.narrow(obj);
    }

    // サービスのメソッドをそのまま委譲するラッパーメソッド群
    public ReturnType methodName(ParamType param) {
        return service.methodName(param);
    }
}
```

`@Value("${corba.<serviceName>.ior}")` で `application.yml` から IOR 文字列を受け取ります。ORB 接続はアプリ起動時に1度だけ確立され、リクエストごとに再接続しません。

#### `writeController(serviceName, servicePackage, methods, controllerDir, basePackage, returnAsDto, stubRoot, clientClassName)`

`@RestController` クラスを生成します。CorbaClient をコンストラクターインジェクションで受け取り、各メソッドへの呼び出しを委譲します。

メソッドコードの組み立ては `appendControllerMethod()` に委譲します。

#### `writeApplicationProperties(outputDir, List<String> serviceNames)`

`src/main/resources/application.yml` を生成します。サービスごとに IOR 設定のプレースホルダーを出力します。

```yaml
corba:
  XxxService:
    ior:
  YyyService:
    ior:
```

---

### コントローラーメソッドのコード生成

#### `appendControllerMethod(sb, method, basePackage, dtoPackage, servicePackage, returnAsDto, stubRoot)`

コントローラーの1メソッド分のコードを生成します。out引数（Holder型）の有無で2つのパスに分かれます。

**out引数なしの場合:**
```
POST /<methodName>
    → RequestDto を受け取り
    → in引数を CORBA 型へ変換（appendInArgConversion）
    → client.methodName() を呼び出し
    → 戻り値を返す（appendDirectReturn）
```

**out引数ありの場合:**
```
POST /<methodName>
    → RequestDto を受け取り
    → in引数を CORBA 型へ変換（appendInArgConversion）
    → out引数は空 Holder を生成
    → client.methodName() を呼び出し
    → callResult と Holder.value を ResponseDto に詰める（appendOutParamResponseDto）
```

#### `appendInArgConversion(sb, param, argName, handlerName, basePackage, servicePackage, method)`

in引数1つ分の DTO→CORBA 変換コードを生成します。型に応じて4通りの変換を行います:

| 型の種別 | 変換方法 |
|---------|---------|
| プリミティブ / String | `req.フィールド` をそのまま代入 |
| `org.omg.CORBA.Any` | `AnyMapper.toAny(req.フィールド)` |
| 配列型 | `Arrays.stream(req.xxx).map(XxxMapper::fromDto).toArray(Xxx[]::new)` |
| 独自型 | `XxxMapper.fromDto(req.フィールド)` |

#### `appendOutParamResponseDto(sb, method, returnType, argNames, ...)`

CORBA 呼び出し後の ResponseDto 構築コードを生成します。`callResult` を `res.returnValue` に、各 `Holder.value` を対応するフィールドに代入します。

#### `appendDirectReturn(sb, method, returnType, callArgs, ...)`

out引数なし・非void メソッドの `return` 文を生成します。プリミティブ/String は直接返し、Any は `AnyMapper.toAnyValue()` を経由し、独自型・配列は `corbaToDtoExpr()` で変換します。

---

### DTO/ResponseDto 生成

#### `generateRequestDto(method, outputRoot, basePkgName)`

サービスメソッドの in パラメーター（Holder型を除く）をパブリックフィールドとして持つ `XxxRequestDto` クラスを JavaPoet で生成します。

フィールドの型は `mapToDtoType()` を使って CORBA 型名から DTO 型名に変換します。

#### `generateResponseDto(method, outputRoot, basePkgName, returnAsDto, servicePackage, stubRoot)`

out引数を持つメソッド専用の `XxxResponseDto` クラスを生成します。

- 非void 戻り値 → `returnValue` フィールド
- 各 out引数 → パラメーター名と同名のフィールド

Holder の value 型の解決には `resolveHolderValueType()` を使用します。

---

### 型解決ユーティリティ

#### `resolveTypeFQN(type, method, fallbackPackage)` → `String`

単純型名を完全修飾名（FQN）に解決します。JavaParser でシンボル解決を行わないため、スタブファイルの import 文をスキャンして `.<単純名>` で終わる import を探します。見つからない場合は `fallbackPackage.単純名` を使います。

#### `resolveHolderValueType(holderType, method, fallbackPackage, stubRoot)` → `String`

Holder 型の `value` フィールドの型名を返します。

- CORBA 標準 Holder（`org.omg.CORBA.IntHolder` 等）はハードコードで対応
- 独自 Holder は `stubRoot` 以下のスタブファイルを JavaParser で解析して `value` フィールドの型を取得する（idlj が sequence typedef に対して生成する配列型 Holder の型を正確に取得するために必要）

#### `corbaToDtoExpr(rhs, valueType, basePackage, dtoPackage, returnAsDto)` → `String`

CORBA 値を DTO へ変換する式（代入右辺として使える文字列）を返します。

- `returnAsDto=false` またはプリミティブ/String: `rhs` をそのまま返す
- 配列型: `Arrays.stream(rhs).map(XxxMapper::toDto).toArray(XxxDto[]::new)`
- 独自型: `XxxMapper.toDto(rhs)`

#### `mapToDtoType(basePackage, typeNameString)` → `String`

CORBA 型名を DTO 型名へマッピングします（例: `com.pkg.Foo` → `basePkg.dto.FooDto`、`org.omg.CORBA.Any` → `basePkg.dto.AnyValue`）。配列型は要素型を再帰変換します。

#### `resolveControllerReturnType(method, ...)` → `String`

コントローラーメソッドの戻り型文字列を解決します:

- out引数あり → `XxxResponseDto`
- void → `Object`（`{"status":"ok"}` を返す）
- プリミティブ/String → そのまま
- `org.omg.CORBA.Any` → `returnAsDto=true` なら `AnyValue`、false なら `org.omg.CORBA.Any`
- 独自型 → `returnAsDto=true` なら `XxxDto`、false なら FQN

#### `convToTypeName(type, dtoPkgName)` → `TypeName`

型名文字列を JavaPoet の `TypeName` へ変換します。`ClassName.bestGuess` は小文字始まりクラス名（例: `pptFoo_struct`）で例外を投げるため、`ClassName.get(pkg, simpleName)` を使って分割変換します。

#### `collectStructClasses(stubSourceRoot)` → `List<ClassOrInterfaceDeclaration>`

スタブから CORBA 構造体クラスを収集します。`isTargetStruct()` でインターフェース・Helper/Holder/POA/Stub・メソッドを持つクラスを除外します。

---

### その他のヘルパー

| 関数 | 用途 |
|-----|-----|
| `isPrimitiveType(type)` | int/long/…/String をプリミティブ相当として判定（配列は要素型で再帰） |
| `isHolderType(typeName)` | 単純名が "Holder" で終わるか判定（CORBA out引数の識別） |
| `toServiceName(serviceIface)` | "Operations" サフィックスを取り除いてサービス名を返す |
| `simpleName(type)` | FQN から単純名を取り出す（例: `"com.pkg.Foo"` → `"Foo"`） |
| `stripHolder(fqn)` | `"FooHolder"` → `"Foo"` のように Holder サフィックスを除去する |
| `capitalize(s)` | 先頭を大文字にする |
| `isTargetStruct(clazz)` | DTO/Mapper 生成対象の構造体クラスかどうかを判定する |

---

## 生成されるプロジェクトの構造

```
<output>/<ServiceName>Api/
 ├─ build.gradle
 ├─ src/main/resources/
 │   └─ application.yml              ← IOR 設定（起動前に入力が必要）
 └─ src/main/java/<basePkg>/
     ├─ Application.java             ← @SpringBootApplication
     ├─ ServletInitializer.java
     ├─ <idlPackage>/                ← idlj 生成スタブ（コピー）
     ├─ controller/
     │   └─ XxxController.java       ← @RestController
     ├─ corba/
     │   └─ XxxCorbaClient.java      ← @Component (ORB接続をシングルトンで保持)
     ├─ dto/
     │   ├─ AnyValue.java
     │   ├─ XxxRequestDto.java       ← in引数フィールド
     │   ├─ XxxResponseDto.java      ← 戻り値 + out引数フィールド（out引数ありのみ）
     │   └─ YyyDto.java              ← CORBA 構造体の DTO
     └─ mapper/
         ├─ AnyMapper.java           ← Any ↔ AnyValue 変換
         └─ YyyMapper.java           ← CORBA 構造体 ↔ DTO 変換
```
