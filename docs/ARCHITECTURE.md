# CORBA-REST ブリッジジェネレーター — 動作機序ドキュメント

## 概要

CORBA サービスの IDL ファイルを入力に、次のどちらかを生成します（`--target`）。

- **rest**（既定）: その CORBA サービスを JSON-RPC 風の REST API として公開する Spring Boot プロジェクト
- **library**: その CORBA サービスを型付きの Java メソッドで呼ぶための共有ライブラリ（jar）。詳しくは「4. 共有ライブラリの生成」

```
IDL ファイル
  │ 1. IdljCompiler   idlj を同じプロセスで動かし、構文解析・パッケージ割り当て・スタブ生成を行う
  │ 2. ModelBuilder   idlj の記号表からサービス・操作・引数・構造体のモデルを作る（型は CorbaType に分類）
  │ 3. SpringBootProjectGenerator（rest）/ LibraryProjectGenerator（library）と各ライター
  ▼
Spring Boot プロジェクト / 共有ライブラリのプロジェクト
```

構造体の DTO / Mapper と型の変換規則（`TypeMapping`）は両方で共通です。共有ライブラリは REST ブリッジとは独立に生成し、REST ブリッジの生成物には影響しません。

## パッケージとクラス構成

```
com.toru_otsuki.bridge_generator   入口と全体の流れ
├─ idl    idlj の実行とモデルの組み立て（工程 1・2）
├─ model  IDL から取り出したモデルと型の分類
└─ gen    Spring Boot プロジェクトの生成（工程 3）
```

依存の向きは `bridge_generator → idl / model / gen`、`idl → model`、`gen → model` です。idlj の内部 API（公開 API ではない）に触れるのは `idl` パッケージだけで、`gen` は `model` だけを使います。

| パッケージ | クラス | 役割 |
|---|---|---|
| (ルート) | `Main` | エントリポイント。引数の誤りは終了コード 2、生成の失敗は 1 |
| | `CliOptions` | コマンドライン引数の解析と usage |
| | `BridgeGenerator` | 全工程を順に実行する |
| | `GenerationException` | 利用者にそのまま見せるエラー |
| `idl` | `IdljCompiler` | idlj（`org.glassfish.corba:idlj`）のコンパイラを継承し、パッケージ割り当ての直前に介入する |
| | `ModelBuilder` | 記号表をたどってモデルを作る |
| `model` | `IdlModel` | サービスと構造体の一覧。複数 IDL の結果の統合 |
| | `Service` / `Operation` / `Param` / `Struct` / `Field` | モデルの各要素 |
| | `CorbaType` | 型の分類（プリミティブ / String / Any / 名前付きの型 / 配列） |

以下はすべて `gen` パッケージです。

| クラス | 役割 |
|---|---|
| `TypeMapping` | 型ごとの DTO 型・CORBA 型・相互変換式 |
| `StubFiles` | スタブファイルの列挙・並列処理・コピー |
| `GenSettings` / `GenContext` | 生成全体の設定 / 1 プロジェクト分の出力先 |
| `SpringBootProjectGenerator` | プロジェクト単位の生成手順 |
| `ProjectScaffolder` | build.gradle・Application・ServletInitializer |
| `DtoGenerator` / `MapperGenerator` | 構造体ごとの `XxxDto` / `XxxMapper` |
| `AnyTypeWriter` / `StringMapperWriter` | `AnyValue`・`AnyMapper` / `StringMapper` |
| `ServiceDtoWriter` | 操作ごとの `RequestDto`・`RpcRequest`・`RpcResult`・`RpcResponse` と application.yml |
| `CorbaClientWriter` | IOR からサービスに接続し、操作を委譲する `CorbaClient` |
| `ControllerWriter` / `ControllerMethodBuilder` | `@RestController` と操作ごとの `POST /操作名` |
| `Target` | 生成するプロジェクトの種類（rest / library） |
| `LibraryProjectGenerator` | 共有ライブラリの生成手順 |
| `ClientWriter` | サービスごとの型付きクライアント `XxxClient` と結果の record |
| `LibraryRuntimeWriter` | `CorbaConnection`・例外の基底クラス・IDL の exception ごとの例外クラス |
| `LibraryScaffolder` | 共有ライブラリの build.gradle・Spring Boot の自動設定・README |

---

## 1. idlj の実行（IdljCompiler）

idlj を外部プロセスではなくライブラリとして使い、IDL ファイルごとに `-emitAll -fclient -i <IDLのディレクトリ> -td <stubRoot> <IDL>` で実行します。プリプロセッサ（`#include`・`#pragma prefix`）と構文解析は idlj のものをそのまま使うので、IDL の意味を自前で推測しません。

idlj は構文解析の後、`preEmit` で記号表に Java のパッケージを書き込みます。`IdljCompiler` はこれを上書きし、その直前に `-pkgPrefix` の対象を足します。

- トップレベルの `module`: `--idl-package-prefix` を指定したときだけ、その接頭辞を付けます。
- グローバルスコープの名前付きの型（`typedef` / `struct` / `enum` / `union` / `interface` など）: `--idl-package-prefix` を、指定が無ければ `generated` を付けます。idlj はそのままだと無名パッケージに出力し、名前付きパッケージから import できないためです。こうするとスタブ同士も完全修飾名で参照し合うので、生成後にスタブを書き換える必要がありません。

パッケージを割り当てると、記号表の親の連なりに接頭辞の module が差し込まれます。IDL 上のスコープ付きの名前（AnyMapper の `AnyValue.type` に使う）は、その前に控えておきます。

idlj は日時をロケールの書式でスタブに書くため、日本語ロケールでは JDK 25 で非推奨の「JST」の警告がファイルごとに出ます。実行中だけ英語ロケールにしています。

## 2. モデルの作成（ModelBuilder・IdlModel・CorbaType）

module と interface の中を宣言順にたどり、次を集めます。Java の名前は idlj の対応規則（`Util.javaQualifiedName` / `Util.holderName`）で求めるので、スタブの名前と必ず一致します。

- **サービス**: `interface`。操作は、その interface で宣言したもの（`methods()`）と、継承したもの（`allMethods()` から宣言したものを除き、ひし形の継承の重複も除く）を分けて持ちます。`attribute` は含めません。
- **構造体**: `struct` と `exception`。リポジトリ ID は idlj が付けた値（`#pragma prefix` 反映済み）をそのまま使います。
- **enum**: 選択肢の名前を宣言順に持ちます。enum を参照する型は、構造体と同じく `Named` にします（変換の形が同じため）。
- **引数の向き**: IDL の `in` / `out` / `inout` をそのまま使い、out / inout の Holder 名も idlj の規則で求めます。
- **ユーザー例外**: 操作の `raises` を宣言順に持ちます。exception の定義は、構造体として `exception` の印を付けて集めます。
- **型の解決**: `typedef` は元の型までたどり、`sequence` と配列の宣言（`long a[3]`）は配列にします。

複数の IDL を渡した場合は IDL ごとの結果を `IdlModel.merge` で統合し、Java のパスで重複を除いて並べます。

型は `CorbaType` の 5 種類に分類します。

| 分類 | 例 | DTO 側の型 | CORBA → DTO | DTO → CORBA |
|---|---|---|---|---|
| `Primitive` | `int`, `long` | 同じ | そのまま | そのまま |
| `Str` | `String` | `String` | `StringMapper.encode` | `StringMapper.decode` |
| `AnyType` | `org.omg.CORBA.Any` | `AnyValue` | `AnyMapper.toAnyValue` | `AnyMapper.toAny` |
| `Named` | `Coverage.Tag`, `Coverage.Status` | `TagDto`, `StatusDto`（※） | `TagMapper.toDto` | `TagMapper.fromDto` |
| `ArrayOf` | `Tag[]`, `String[][]` | 要素の DTO 型の配列 | 要素ごとに変換（`Arrays.stream`、入れ子はラムダ） | 同左 |

※ DTO の名前は `--dto-prefix` / `--dto-suffix` で変えられます（`Naming`）。空にすると idlj のクラスと同じ単純名になりますが、生成コードは完全修飾名か JavaPoet の import で区別するので問題ありません。ただし生成コードは `String` などを単純名で書き、REST ブリッジは操作ごとのクラス（`XxxRpcRequest` など）を同じ dto パッケージに置くため、それらと重なる名前は生成前に `NameConflicts` がエラーにします。

String は、CORBA サーバーが ISO-8859-1 として送ってくる自分の文字コード（既定は SJIS）のバイト列を読み替えるため、`--return-mode` によらず常に `StringMapper` を通します。文字コードは生成アプリの `corba.string-charset` で決まり、`--string-charset` はその既定値です（application.yml に書かれ、`@Value` の既定値にもなります）。`none` なら読み替えません。Mapper は静的メソッドから呼ぶため、`StringMapper` 自身を Bean にして、起動時に設定値を静的フィールドへ移します。`--return-mode=raw` のときは、戻り値と out 引数の Any と名前付きの型を変換せず CORBA 型のまま返します（リクエスト側は常に DTO です）。

enum の DTO は Java の enum（`StatusDto`）なので、JSON では選択肢の名前の文字列（`"ACTIVE"`）になります。CORBA では宣言順の番号で送るため、Mapper は番号どうし（DTO の `ordinal()` と idlj のクラスの `value()` / `from_int`）で変換します。JSON に無い名前を送ると、Jackson が読み込めずに 400 Bad Request になります。`--return-mode=raw` では idlj の enum のクラスがそのまま返りますが、そのままでは Jackson が書けないため、選択肢の名前の文字列で書く Jackson のモジュール（`mapper/CorbaEnumJsonModule`）も生成します。

この規則は `TypeMapping` の 1 か所だけで定義し、構造体 Mapper・Controller・RequestDto/RpcResult のすべてがそれを使います。

## 3. Spring Boot プロジェクトの生成

`--single-project` なら全サービスを 1 プロジェクト（`<output-root>/<project-name>`）に、そうでなければサービスごとに `<output-root>/<サービス名>Api` を生成します。

```
setupProject（プロジェクトごとに 1 回）
 ├─ build.gradle / Application.java / ServletInitializer.java
 ├─ スタブ一式をコピー（並列）
 ├─ dto/AnyValue.java、mapper/AnyMapper.java、mapper/StringMapper.java
 ├─ corba/CorbaOrb.java（アプリで 1 つの ORB）
 ├─ 構造体・enum ごとに dto/XxxDto.java、mapper/XxxMapper.java
 └─ --return-mode=raw で enum があれば mapper/CorbaEnumJsonModule.java
generateService（サービスごと。操作が無ければ何もしない）
 ├─ 操作ごとに dto/XxxRequestDto・XxxRpcRequest・XxxRpcResult・XxxRpcResponse
 ├─ corba/CorbaClient.java（単一プロジェクト時は <サービス名>CorbaClient.java）
 └─ controller/<サービス名>Controller.java
application.yml（corba.<サービス名>.ior の欄をサービスごとに出力）
```

### ORB の持ち方（CorbaOrb）と、同じ Tomcat に複数のアプリを置く場合

ORB はアプリで 1 つ（`CorbaOrb` Bean）にし、全サービスの `CorbaClient` で共有して、アプリの終了時に破棄します。共有ライブラリの `CorbaConnection` も同じ方針です。

glassfish-corba の ORB は、既定では JVM で共有される JMX の MBean サーバーに、`com.sun.corba:...ORB-Root[orb]` のような**固定の名前**で自分を登録します。そのため、同じ Tomcat に生成したアプリを 2 つ以上置く（パラレルデプロイを含む）と、2 つ目以降の ORB が `InstanceAlreadyExistsException` で初期化に失敗し、続けて IIOP の待ち受けポートの初期化も壊れます（`Unable to create IIOP listener` / `BindException`）。これを避けるため、次のようにしています。

- ORB は既定で `com.sun.corba.ee.ORBRegisterMBeans=false`（MBean を登録しない）で作ります。`corba.orb.*` の設定は ORB にそのまま渡し、既定値より優先します（例: `corba.orb.[com.sun.corba.ee.ORBRegisterMBeans]=true`）。
- `AnyMapper` は Any をアプリの ORB で作ります（`AnyMapper.useOrb`）。シングルトン ORB（`ORB.init()`）で struct 入りの Any を作ると、シングルトン ORB が内部で通信用の ORB を固定の名前（`_$$$_INTERNAL_FULL_ORB_ID_$$$_`）で作って MBean を登録するため、同じ衝突が起きます。
- gmbal のシステムプロパティ（`org.glassfish.gmbal.no.multipleUpperBoundsException`）も ORB を作る前に設定するので、JVM 引数は要りません。

Tomcat 11.0.27 で、修正前は 2 つ目のアプリとパラレルデプロイがどちらも失敗し、修正後はどちらも成功することを確かめています。アンデプロイ時に Tomcat が ORB の ThreadLocal を「メモリリークの可能性」として警告しますが、Tomcat がリクエスト処理スレッドを入れ替えて回収するので、実害はありません（停止しないスレッドは残りません）。

### 生成される Controller の動き

1. `POST /操作名` で `XxxRpcRequest` を受け取り、`req = rpc.params` とする。in / inout 引数がある操作で `params` が無ければ、既定値で補って呼び出すことはせず 400 Bad Request で止める。
2. 引数を向きごとに用意する。
   - in: DTO → CORBA に変換する。
   - out: 空の Holder を用意する。
   - inout: DTO → CORBA に変換した値を入れた Holder を用意する。string・struct・enum・配列は省略すると CORBA に渡せないので、`params` に無ければ 400 Bad Request（`ResponseStatusException`）で止める。数値などのプリミティブは省略すると 0 や false になる。

   in / inout は `XxxRequestDto` に、out / inout は `XxxRpcResult` に現れる（inout は両方）。
3. `CorbaClient` 経由で呼び出す。
4. 戻り値と各 `Holder.value` を変換して `XxxRpcResult`（戻り値は `@JsonProperty("return")`）に詰め、`XxxRpcResponse`（`jsonrpc: "2.0"`, `id: 1`）で返す。

### AnyMapper

Any は JSON では `{"type": 型名, "value": 値}`（`AnyValue`）で表し、両方向に変換します。

- **CORBA → JSON（`toAnyValue`）**: TypeCode の種類ごとに値を取り出します。struct と enum は、生成時に型ごとに登録したリポジトリ ID（idlj が付けたもの）で Helper と Mapper を引き、`type` に IDL 上の名前（例: `Coverage.Item`）を入れます。enum の `value` は選択肢の名前です。登録の無い enum は番号を入れます。
- **JSON → CORBA（`toAny`）**: `type` が基本型（`long`・`string` など）ならその型で入れます。IDL の構造体名・enum 名なら、JSON の値（Jackson では `LinkedHashMap` や文字列）を DTO に読み替え、Mapper と Helper で Any に入れます。`any` は入れ子として再帰的に処理します。そのため、CORBA から返ってきた JSON をそのまま送り返せます。
- sequence・union・objref などは、Any に入れる TypeCode を JSON から組み立てられないため、`toAny` では `IllegalArgumentException` にします（CORBA → JSON 方向は従来どおり）。
- `string` は他の文字列と同じく `StringMapper` で文字コードの読み替えをします。
- Any はアプリの ORB で作ります（理由は「ORB の持ち方」を参照）。アプリの ORB が無いときだけシングルトン ORB を使います。

構造体が多いと 1 メソッドが JVM の 64KB 上限を超えるため、登録処理は 50 件ずつのメソッドに分けます。

### raises（ユーザー例外）の制約

REST ブリッジは `raises` に対応していません。スタブのメソッドがチェック例外を投げるのに、生成する `CorbaClient` が宣言していないため、`raises` のある IDL では生成したプロジェクトがコンパイルできません。共有ライブラリは対応しています。

TODO: 対応するには、エラー時の JSON の形（例外の型名と中身の載せ方）と HTTP ステータスを決める必要があります（`CorbaClientWriter` に TODO コメントあり）。

### 継承した操作

REST ブリッジは、インターフェースで宣言した操作だけを公開し、親から継承した操作は公開しません。単一プロジェクトでは URL が `/操作名` なので、親と子の両方で公開すると同じ URL ができて起動に失敗するためです。継承した操作は親のサービスの API から呼びます。CORBA サーバーが子のオブジェクトしか公開していなければ、親の `corba.<親>.ior` に子の IOR を設定します（子は親の操作をすべて持つので、そのまま呼べます）。共有ライブラリは継承した操作も子のクライアントに入れます。

## 4. 共有ライブラリの生成

`--target=library` で、全サービスを 1 つの java-library プロジェクト（`<output-root>/<project-name>`、既定は `corba-client`）にまとめます。利用者には DTO と Java の型だけを見せ、Holder・Any・CORBA の型・文字コード・ORB は隠します。

```
<project>/
 ├─ build.gradle / settings.gradle / README.md（利用者向けの使い方）
 ├─ src/main/java/
 │   ├─ スタブ一式
 │   └─ <base-package>/
 │       ├─ dto/       構造体ごとの XxxDto、AnyValue（record）
 │       ├─ mapper/    構造体ごとの XxxMapper、AnyMapper、StringMapper（Spring に依存しない版）
 │       ├─ client/    サービスごとの XxxClient、CorbaConnection
 │       ├─ exception/ IDL の exception ごとの XxxException、CorbaUserException、CorbaCallException
 │       └─ spring/    CorbaClientAutoConfiguration
 └─ src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

- **クライアント（`XxxClient`）**: in / inout 引数をメソッドの引数に、戻り値を戻り値にします。out / inout 引数がある操作は、戻り値と out / inout 引数をまとめた record（`XxxClient.操作名Result`。名前は `--result-prefix` / `--result-suffix` で変えられる）を返します。inout の string・struct・enum・配列が null なら `NullPointerException` にします。サービスのオブジェクト参照は最初の呼び出しで解決するので、CORBA サーバーが止まっていても利用側は起動できます。親のインターフェースから継承した操作（祖先の分も含む。ひし形の継承でも 1 回ずつ）もメソッドにします。
- **例外**: `raises` のユーザー例外は、DTO を `detail()` で持つ `XxxException`（名前が Exception で終わる exception はそのままの名前）に、CORBA のシステム例外は `CorbaCallException` に包み直します。どちらも `RuntimeException` です。
- **ORB**: `CorbaConnection` が 1 つ持ち、閉じるときに破棄します。REST ブリッジの `CorbaOrb` と同じく、MBean を登録しない設定と、gmbal のシステムプロパティの設定をします。`corba.orb.*` は ORB にそのまま渡します。同じ Tomcat に利用アプリを複数置けますが、jar は Tomcat の共有 lib ではなく各アプリの `WEB-INF/lib` に置きます（`StringMapper` の文字コード設定が static のため）。
- **Spring Boot**: 利用側が Spring Boot なら、jar を依存に入れるだけで自動設定が働きます。`CorbaConnection` を Bean にし（終了時に close）、`corba.<サービス名>.ior` が設定されたサービスだけクライアントを Bean にします。`corba.string-charset` は起動時に `StringMapper.configure` へ渡します。Spring Boot はコンパイル時だけの依存で、Spring を使わない利用者は `CorbaConnection` と `XxxClient` を自分で作ります。
- **Any**: ライブラリの `AnyValue` は不変の record で、構造体の値は DTO そのものです（REST では JSON から読んだ Map）。`AnyValue.of(dto)` で型名を補って作り、`as(Class)` で型を指定して取り出します。
- **配布**: jar を直接渡す想定なので、依存（glassfish-corba-omgapi・glassfish-corba-orb・gmbal）は利用側で書きます。生成する README に一覧を載せます。

## テスト

`./gradlew test` で次を実行します。網羅用 IDL は `src/test/resources/idl/coverage.idl` です。ユーザー例外と継承を使う `errors.idl` は、REST ブリッジが `raises` に対応していないため共有ライブラリのテストだけで使います。

| テスト | 内容 |
|---|---|
| `GoldenTest` | 網羅用 IDL から生成したプロジェクト（idlj スタブのコピーを除く）を `src/test/resources/golden/` と比較。REST の 2 通り（dto / raw）と共有ライブラリ |
| `GeneratedCodeCompilesTest` | 生成したソースを spring-web・spring-context・Spring Boot の自動設定・Jackson・CORBA API だけをクラスパスにしてコンパイル |
| `IdljCompilerTest` | グローバル型のパッケージ、引数の向きと Holder、typedef の解決、リポジトリ ID、raises、継承した操作、文法エラー |
| `CliOptionsTest` / `TypeMappingTest` | 各部品の単体テスト |

生成ロジックを意図して変えたときは、`./gradlew test -Dgolden.update=true` で期待値を更新し、差分をレビューしてからコミットします。
