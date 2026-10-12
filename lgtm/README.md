# LGTMスタック(ローカルWindows版)

| 役割 | コンポーネント | URL |
|---|---|---|
| **L**ogs | Loki 3.7 | <http://127.0.0.1:3100> |
| **G**rafana | Grafana 13.2 | <http://127.0.0.1:3000>|
| **T**races | Tempo 3.1 | <http://127.0.0.1:3200> / OTLP gRPC 4317, HTTP 4318 |
| **M**etrics | Prometheus 3.15(Mimirの代替) | <http://127.0.0.1:9090> / OTLP HTTP <http://127.0.0.1:9090/api/v1/otlp/v1/metrics> |
| (収集) | windows_exporter 0.32(このPC自身のメトリクス) | <http://127.0.0.1:9182/metrics> |

## 使い方

1. `start-all.bat` … 全てのコンポーネントを起動する
2. `status.bat` … 各起動状態を確認する
3. `stop-all.bat` … 全てのコンポーネントを停止する

個別に操作したい場合は各コンポーネント用の

- `scripts\start-<name>.bat`
- `scripts\stop-<name>.bat`

を使用してください。

## 動作確認

`send-sample.bat`を用いて、サンプルのトレース＋ログを送ることが出来ます。

## Prometheusに監視対象を追加する

`conf/prometheus/targets/`に`<名前>.yml`を置くと自動反映されます（再起動不要）。書き方は同じフォルダの`_example.yml.sample`を参照してください。反映状況は<http://127.0.0.1:9090/targets>で確認できます。

## データの保持期限

データの保持期限はすべて7日です。それぞれ以下で設定しています。

| コンポーネント | 保持期限 | 設定箇所 |
|---|---|---|
| Prometheus | 7日 | `conf/prometheus/prometheus.yml`の`storage.tsdb.retention.time: 7d` |
| Loki | 7日 | `conf/loki/loki.yaml`の`limits_config.retention_period: 168h`|
| Tempo | 7日 | `conf/tempo/tempo.yaml`の`block_retention: 168h`(backend_scheduler/backend_workerの2箇所) |
| Grafanaログ | 7日 | 既定値|

## フォルダ構成

```plain
/
　├ conf/           ... 設定ファイル
　├ data/           ... データファイル
　├ scripts/        ... スクリプト
　├ system/         ... コンポーネント
　├ start-all.bat   ... 起動用bat
　└ stop-all.bat    ... 停止用bat
```

- フォルダの場所はbat自身の位置(`%~dp0`)から解決し、設定ファイル内では環境変数
  `LGTM_ROOT`/`LGTM_HOME`を展開して絶対パスにしています（つまりポータブルです）。
- 停止時は、このフォルダのexeに一致するプロセスだけを対象にし、まずグレースフル終了を試みます。
- すべて全インターフェース(0.0.0.0)で待ち受けるので、同じネットワークの他マシンからアクセスできます。
  Grafanaはログイン不要の管理者権限、Prometheus/Loki/Tempoは認証なしのため、信頼できるネットワークでのみ使用してください。
- データを初期化したい場合は、停止後に`data/`配下の各ディレクトリの中身を削除してください。
