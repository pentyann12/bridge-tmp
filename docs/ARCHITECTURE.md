# CORBA-REST ブリッジジェネレーター — 動作機序ドキュメント

## 概要

CORBA サービスの IDL ファイルを入力に、その CORBA サービスを JSON-RPC 風の REST API として公開する Spring Boot プロジェクトを生成します。

```
IDL ファイル
  │ 1. IdlScanner     #include をたどり、#pragma prefix と module 名を読む
  │ 2. IdljRunner     idlj で Java スタブを生成
  │ 3. StubRelocator  デフォルトパッケージに出たスタブを名前付きパッケージへ移す
  │ 4. StubIndex      スタブを一度だけパースして索引化
  │    StubModel      サービス・操作・引数・構造体のモデルを作る（型は CorbaType に分類）
  │ 5. SpringBootProjectGenerator と各ライター
  ▼
Spring Boot プロジェクト
```

## パッケージとクラス構成

```
com.toru_otsuki.bridge_generator   入口と全体の流れ
├─ idl    IDL の読み取りと idlj の起動（工程 1・2）
├─ stub   スタブの再配置・索引・モデル・型の分類（工程 3・4）
└─ gen    Spring Boot プロジェクトの生成（工程 5）
```

依存の向きは `bridge_generator → idl / stub / gen`、`gen → stub` です。`gen` は JavaParser の構文木に触れず、`stub` のモデルだけを使います。

| パッケージ | クラス | 役割 |
|---|---|---|
| (ルート) | `Main` | エントリポイント。引数の誤りは終了コード 2、生成の失敗は 1 |
| | `CliOptions` | コマンドライン引数の解析と usage |
| | `BridgeGenerator` | 1〜5 の全工程を順に実行する |
| | `GenerationException` | 利用者にそのまま見せるエラー |
| `idl` | `IdlScanner` | IDL を正規表現で読み、`#pragma prefix` と `-pkgPrefix` の対象名を返す |
| | `IdljRunner` | idlj を argfile 経由で起動する |
| `stub` | `StubRelocator` | デフォルトパッケージのスタブ移動と import の整合 |
| | `StubFiles` | スタブファイルの列挙・並列処理・コピー |
| | `StubIndex` | スタブの構文木の索引。サービス（`*Operations`）と構造体の列挙、完全修飾名での検索 |
| | `StubModel` | `Service` / `Operation` / `Param` / `Struct` / `Field` のモデルと、その組み立て |
| | `CorbaType` | 型の分類（プリミティブ / String / Any / 名前付きの型 / 配列） |

以下はすべて `gen` パッケージです。

| クラス | 役割 |
|---|---|
| `TypeMapping` | 型ごとの DTO 型・CORBA 型・相互変換式 |
| `GenSettings` / `GenContext` | 生成全体の設定 / 1 プロジェクト分の出力先 |
| `SpringBootProjectGenerator` | プロジェクト単位の生成手順 |
| `ProjectScaffolder` | build.gradle・Application・ServletInitializer |
| `DtoGenerator` / `MapperGenerator` | 構造体ごとの `XxxDto` / `XxxMapper` |
| `AnyTypeWriter` / `StringMapperWriter` | `AnyValue`・`AnyMapper` / `StringMapper` |
| `ServiceDtoWriter` | 操作ごとの `RequestDto`・`RpcRequest`・`RpcResult`・`RpcResponse` と application.yml |
| `CorbaClientWriter` | IOR からサービスに接続し、操作を委譲する `CorbaClient` |
| `ControllerWriter` / `ControllerMethodBuilder` | `@RestController` と操作ごとの `POST /操作名` |

---

## 1. IDL の静的解析（IdlScanner）

`#include "..."` を、そのファイルからの相対パスで再帰的にたどって関連ファイルを集めます。循環参照と存在しないファイルはスキップします。

- `#pragma prefix "..."`: 関連ファイル中で最初に見つかった値。複数の IDL を渡した場合は、最初に値が見つかった IDL のものを使います。AnyMapper のリポジトリ ID に使います。
- `-pkgPrefix` の対象: `module` 名（重複除外・出現順）。module が無ければトップレベルの `interface` / `struct` / `union` / `enum` / `exception` の名前。

## 2. idlj の実行（IdljRunner）

`java -jar tools/idlj.jar -emitAll -I <IDLのディレクトリ> -fclient [-pkgPrefix <対象> <prefix>]... -td <stubRoot> <IDL>` を実行します。IDL が多いとコマンド長の上限を超えるため、引数は `stubRoot/idlj-args/<IDL名>.args` に書き出し `java @argfile` で渡します。

## 3. スタブの再配置（StubRelocator）

グローバルスコープで定義した型（`typedef sequence` など）は idlj が `stubRoot` 直下（無名パッケージ）に出力し、名前付きパッケージから import できません。そこで次の処理をします。

1. 移動先パッケージを決める: `--idl-package-prefix` → 既存スタブの package 宣言の共通プレフィックス → `generated` の順。
2. 直下の `.java` に package 宣言を付けて移動先へ移す。
3. 全スタブについて、1 ファイルにつき 1 回の読み書きで次を順に適用する（`ImportFixer`）。
   - `import Foo;` を `import 移動先.Foo;` に書き換える（移動先と同じパッケージなら削除）。
   - import せずにクラス名だけで参照しているファイルに、完全修飾 import を足す。
   - 残ったパッケージ無しの `import X;` を削除する。

実際の IDL ではグローバル型が 1 万近くになるため、「ファイル数 × クラス数」の検索にならないよう、ファイル中の import 文や識別子から逆に引いています。ファイル単位の処理は並列に実行します。

## 4. スタブのモデル化（StubIndex・StubModel・CorbaType）

スタブを一度だけ並列にパースし、以降は構文木ではなくモデルを使います。

- **サービス**: 名前が `Operations` で終わるインターフェース。サービス名は `Operations` を除いたもの。
- **構造体**: interface でなく、名前が Helper / Holder / Operations / POA / Stub で終わらず、`_` で始まらず、メソッドを持たないクラス。
- **型の解決**: JavaParser はシンボル解決をしないので、単純名はそのファイルの import 文から逆引きし、見つからなければ同じパッケージとみなします。
- **out / inout 引数**: 型の単純名が `Holder` で終わる引数。`value` の型は、CORBA 標準の Holder（`IntHolder` など）は固定の対応で、IDL 由来の Holder はスタブの `value` フィールドから求めます（sequence の typedef は `Tag value[]` のように配列になるため）。

型は `CorbaType` の 5 種類に分類します。

| 分類 | 例 | DTO 側の型 | CORBA → DTO | DTO → CORBA |
|---|---|---|---|---|
| `Primitive` | `int`, `long` | 同じ | そのまま | そのまま |
| `Str` | `String` | `String` | `StringMapper.encode` | `StringMapper.decode` |
| `AnyType` | `org.omg.CORBA.Any` | `AnyValue` | `AnyMapper.toAnyValue` | `AnyMapper.toAny` |
| `Named` | `Coverage.Tag` | `TagDto` | `TagMapper.toDto` | `TagMapper.fromDto` |
| `ArrayOf` | `Tag[]`, `String[][]` | 要素の DTO 型の配列 | 要素ごとに変換（`Arrays.stream`、入れ子はラムダ） | 同左 |

String は、CORBA サーバーが ISO-8859-1 として送ってくる SJIS バイト列を読み替えるため、`--return-mode` によらず常に変換します。`--return-mode=raw` のときは、戻り値と out 引数の Any と名前付きの型を変換せず CORBA 型のまま返します（リクエスト側は常に DTO です）。

この規則は `TypeMapping` の 1 か所だけで定義し、構造体 Mapper・Controller・RequestDto/RpcResult のすべてがそれを使います。

## 5. Spring Boot プロジェクトの生成

`--single-project` なら全サービスを 1 プロジェクト（`<output-root>/<project-name>`）に、そうでなければサービスごとに `<output-root>/<サービス名>Api` を生成します。

```
setupProject（プロジェクトごとに 1 回）
 ├─ build.gradle / Application.java / ServletInitializer.java
 ├─ スタブ一式をコピー（並列）
 ├─ dto/AnyValue.java、mapper/AnyMapper.java、mapper/StringMapper.java
 └─ 構造体ごとに dto/XxxDto.java、mapper/XxxMapper.java
generateService（サービスごと。操作が無ければ何もしない）
 ├─ 操作ごとに dto/XxxRequestDto・XxxRpcRequest・XxxRpcResult・XxxRpcResponse
 ├─ corba/CorbaClient.java（単一プロジェクト時は <サービス名>CorbaClient.java）
 └─ controller/<サービス名>Controller.java
application.yml（corba.<サービス名>.ior の欄をサービスごとに出力）
```

### 生成される Controller の動き

1. `POST /操作名` で `XxxRpcRequest` を受け取り、`req = rpc.params` とする。
2. in 引数を DTO → CORBA に変換し、out 引数には空の Holder を用意する。
3. `CorbaClient` 経由で呼び出す。
4. 戻り値と各 `Holder.value` を変換して `XxxRpcResult`（戻り値は `@JsonProperty("return")`）に詰め、`XxxRpcResponse`（`jsonrpc: "2.0"`, `id: 1`）で返す。

### AnyMapper

`toAnyValue` は TypeCode の種類ごとに値を取り出します。struct は、生成時に構造体ごとに登録した `IDL:<#pragma prefix>/<モジュール>/<名前>:1.0` のリポジトリ ID で Helper と Mapper を引きます。構造体が多いと 1 メソッドが JVM の 64KB 上限を超えるため、登録処理は 50 件ずつのメソッドに分けます。

## テスト

`./gradlew test` で次を実行します。網羅用 IDL は `src/test/resources/idl/coverage.idl` です。

| テスト | 内容 |
|---|---|
| `GoldenTest` | 網羅用 IDL から生成したプロジェクト（idlj スタブのコピーを除く）を `src/test/resources/golden/` と比較 |
| `GeneratedCodeCompilesTest` | 生成したソースを spring-web・spring-context・jackson-annotations・CORBA API だけをクラスパスにしてコンパイル |
| `CliOptionsTest` / `IdlScannerTest` / `StubRelocatorTest` / `TypeMappingTest` | 各部品の単体テスト |

生成ロジックを意図して変えたときは、`./gradlew test -Dgolden.update=true` で期待値を更新し、差分をレビューしてからコミットします。
