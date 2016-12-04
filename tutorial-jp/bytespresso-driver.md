
Bytespresso: Bytespresso Driver
==

Bytespressoは、JavaのLambda式を実行中のJVMの外側で実行する仕組みを提供する。この実行の仕組みを司るJavaクラスがBytespresso Driverである。

代表的なBytespresso DriverであるStdDriverを使うアプリケーションの実行フローを以下に示す。

<!---
```sequence
Application -> StdDriver : 1. Call 'invoke' with\nJava lambda
StdDriver --> StdDriver : 2. Deep reification &\n Generate C Source
StdDriver -> C Compiler : 3. Run CC
C Compiler -> a.out : 4. Compile and link
StdDriver -> a.out : 5. Run a.out
a.out --> StdDriver : 6. Remote (JVM) call
StdDriver --> a.out : 7. Return value of Remote call
a.out -> StdDriver : 8. Return value from a.out
StdDriver -> Application : 9. Return value of \nthe Java lambda
```
--->
![StdDriver Server Sequence](img/StdDriver-Server.png?raw=true)

1. Java lambda式を引数にStdDriver.invokeを呼び出す。このlambda式の記述には以下の制限がある。
	- (lambda式の)引数をとらない
	- (lambda式の)返り値はvoid, int, double いずれかである
	- Standard Java APIが利用できない
		- String型においてはcharAt及びlengthメソッドのみ利用できる。+演算子が使えない。
		- System.out.println()及び、System.err.println()は利用できない。そのかわりに javassist.offload.lib.Util.print(), javassist.offload.lib.Util.printer.p(), javassist.offload.lib.Util.printer.ln() を使う。
	- 例外（exception)処理を利用することができない。すなわち try catchやthrowを書くことができない。NullPointerExceptionやArrayIndexOutOfBoudsExceptionの発生はSegmentation faultを引き起こす。
	- デフォルトではGCが行われない（呼び出し元のApplicationを動かしているJVMとは別のプロセスでの実行となる）
2. 1で引数として与えられたlambda式を、Deep reificationによってIntermediate Representation(IR)に変換、その後Cコードに変換する。
- Deep reificationは与えられたコード（lambda式）を実行するために必要なメソッドやstatic変数を全て抽出する作業である。
- 変換後のCコードが高いパフォーマンスを発揮できるよう、変換対象のlambda式から呼ばれるJavaコードにはJava Annotation記法でBytespresso向けの特殊な変換を指定できる。Nativeコード置き換えや、変換コードブロック（Java lambda式）内での変数のimmutable性の指定がそれにあたる。
- 変換対象のJava lambda式から呼ばれるJavaコードには、JVMを呼び出すコード（@Remoteアノテーションが付与されているメソッドの呼び出し）を書くことができる。
3. Cコンパイラを起動し、変換されたCコードをコンパイルする。
4. Cコンパイラは実行バイナリa.outを吐き出す。
5. 実行バイナリa.outを動かす。この時、a.outの入出力としてはStdDriverとの間に貼られたPIPEを使う。
6. a.outにJVMを呼び出すコード（@Remoteが付与されているメソッド呼び出し）がある場合、PIPEを経由してStdDriverのあるJVMに実行を依頼する
7. 6の呼び出し結果をPIPE経由でa.outに戻す
8. a.outの実行終了と実行結果をPIPE経由でStdDriverに戻す
9. 1のJava lambda式の実行結果として8の結果をApplicationに返す

StdDriverの内部処理では、5のa.out実行の際に、javassist.offload.Serverオブジェクト（以下、単にServerオブジェクトと呼ぶ）が作られ、このオブジェクトがPIPEを使ったa.outとのやりとりを担う。@Remoteが付与されたメソッドの呼び出しはServerオブジェクトによって実行される。StdDriverの場合は、Serverオブジェクトは、StdDriver及びApplicationを実行しているJVMの中に作られるが、Driverによっては、このServerオブジェクトが他のJVMに作られる場合がある。その場合、@Remoteが付与されたメソッドの呼び出しを実行するJVMが、Applicationを実行しているJVMと異なる。

例えば、MPIDriverの場合、Serverオブジェクトは、a.outプロセスからforkされた別プロセスのJVMに作られる。

<!---
```sequence
Application -> MPIDriver : 1. Call 'invoke' with\nJava lambda
MPIDriver --> MPIDriver : 2. Deep reification &\n Generate C Source
MPIDriver -> mpicc : 3. Run CC
mpicc -> a.out : 4. Compile and link
MPIDriver -> mpiexec : 5. Run mpiexec a.out
mpiexec -> a.out : 6. Spawn MPI process
a.out -> Server : 7. Start JVM
a.out --> Server : 8. Remote (JVM) call
Server --> a.out : 9. Return value of Remote call
a.out -> MPIDriver : 10. Return value from a.out
MPIDriver -> Application : 11. Return value of \nthe Java lambda
```
--->

![MPIDriver Server Sequence](img/MPIDriver-Server.png?raw=true)


この場合は、変換後のCコードであるbytespresso.c（a.outのソースコード）におけるmain関数で、fork&execが行われ、Serverが起動される。Serverを実行するJVMはMPI process毎に作られることに留意されたい。
