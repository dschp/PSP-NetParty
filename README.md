# PSP NetPartyについて

PSP NetParty (ネトパ・PNP) は、XLink KaiやMHP Tunnelのように\
インターネットを経由したPSPの通信プレイを可能にするトンネリングソフトです。\
部屋やチャットなどにはTCP、PSPのトンネル通信にはTCPかUDPを選択して使用します。

**クライアント側のポート開放は基本的に必要ありません。**

- このソフトウェアはフリーソフト(GPL)です。
- 開発者・テスターによる十分なテストをした上での配布となっておりますが、\
  このソフトウェアを利用した事によるいかなる損害も作者は一切の責任を負いません。\
  自己の責任の上で使用して下さい。
- 配布、転載などは自由に行なってかまいません。

## 公式サイト Official Site
https://github.com/montehunter/PSP-NetParty/wiki


## ビルド方法

SWTとjNetPcapについては、それぞれのOS用のものをDLしてください\
jNetPcapはプロジェクトルートに、SWTはlib_swtへ配置してください

Eclipseで開発する場合は、ダウンロードしたSWTのZipをプロジェクトとしてインポートするか\
lib_swtにあるswt.jarへクラスパスを設定しなおしてください

antのビルドでは、build.propertiesを各自用意して、\
publish_directoryをPSP NetPartyのjarを書き出すディレクトリに指定してください
