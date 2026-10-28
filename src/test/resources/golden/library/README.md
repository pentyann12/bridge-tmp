# corba-client

IDLから生成した、CORBAサービスを型付きのJavaメソッドで呼ぶためのライブラリです。
Holder・Any・CORBAの型・文字コードの読み替え・ORBの管理は、ライブラリの中で行います。

## ビルド

```
gradle jar
```

`build/libs/corba-client-0.0.1.jar` ができます（`-sources.jar` はIDEでの参照用）。

## 利用側の設定（Spring Boot）

jarと、このライブラリが使う依存を追加します。jarを直接使うため、依存は利用側で書く必要があります。

```groovy
dependencies {
  implementation files('libs/corba-client-0.0.1.jar')
  implementation 'org.glassfish.corba:glassfish-corba-omgapi:5.0.3'
  runtimeOnly 'org.glassfish.corba:glassfish-corba-orb:5.0.3'
  runtimeOnly 'org.glassfish.gmbal:gmbal:4.1.2'
}
```

application.yml に、使うサービスのIORを書きます。IORを書いたサービスのクライアントだけがBeanになります。

```yaml
corba:
  # CORBAサーバーが文字列に使う文字コード（noneなら読み替えない）
  string-charset: SJIS
  ItemService:
    ior: IOR:...
  Guarded:
    ior: IOR:...
  ClockService:
    ior: IOR:...
```

| サービス | クライアント | IORの設定 |
|---|---|---|
| `ItemService` | `com.generated.client.ItemServiceClient` | `corba.ItemService.ior` |
| `Guarded` | `com.generated.client.GuardedClient` | `corba.Guarded.ior` |
| `ClockService` | `com.generated.client.ClockServiceClient` | `corba.ClockService.ior` |

## 呼び出し方

```java
@Service
class MyService {
  private final ItemServiceClient client;

  MyService(ItemServiceClient client) {
    this.client = client;
  }
}
```

- 引数・戻り値は、構造体ならDTO（`com.generated.dto.XxxDto`）、文字列は `String` です。
- out/inout引数がある操作は、戻り値とout/inout引数をまとめたrecord（`XxxClient.操作名Result`）を返します。
- サービスへの接続は、最初の呼び出しのときに行います。CORBAサーバーが止まっていてもアプリは起動できます。

### 例外

- IDLの `raises` で宣言された例外は、`com.generated.exception.<名前>Exception` で投げられます。中身は `detail()` でDTOとして取り出せます。
- 接続できない・通信が切れたなどのCORBAのシステム例外は、`com.generated.exception.CorbaCallException` で投げられます。

どちらも `RuntimeException` です。

### Any

`any` 型は `com.generated.dto.AnyValue`（型名と値の組）で受け渡します。

```java
AnyValue v = AnyValue.of(tagDto);                  // 構造体のDTOから作る（型名は自動で決まる）
AnyValue n = new AnyValue("long", 42);              // 基本型は型名を指定する
Optional<TagDto> tag = v.as(TagDto.class);          // 型を指定して取り出す
```

sequence・union などはAnyに入れて送れません（受け取りはできます）。

## Spring Bootを使わない場合

```java
try (CorbaConnection connection = new CorbaConnection()) {
  var client = new ItemServiceClient(connection, ior);
  ...
}
```

文字コードを変えるときは、最初の呼び出しの前に `StringMapper.configure("Windows-31J")` を呼びます。
