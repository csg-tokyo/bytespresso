
Bytespresso: Annotation @Native
==

仕様
--
- 対象: メソッド、コンストラクタ
	- 引数及び、返り値の型は primitiveな型またはその配列に限られる
- 記法: @Native(String)
	- 引数の文字列でNative実行対象のCコードを与える
	- 引数のCコード文字列において、v1, v2, ... は@Nativeアノテーションを付与したJavaメソッドの第1引数、第2引数・・・を意味する

説明
--
@Nativeアノテーションをつけた関数を呼び出すコードがCコードに変換された時、実行されるコードは、@Nativeの引数で渡されるコード（文字列）である。

@Nativeアノテーションをつけた関数の呼び出しが変換対象のJava Lambda式にあった場合、BytespressoのC言語トランスレータは@Nativeの引数文字列をfunction bodyとするC関数を出力する。そして、そのJava関数を呼び出すコードを、出力されたC関数を呼び出すコードに置き換える。

このfunction bodyの中では、`v1` `v2` ... といった`v` で始まる変数が定義済みの変数として使えるが、これは変換ターゲットとなるJava関数の仮引数がC言語の変数に変換されたものである。仮引数の型はprimitiveなものだけが許されている。


使用例
--

Javaメソッド maxをC関数で置き換える事例を示す。

以下のコードにおいては、StdDriverに渡されるJava Lambda式の中に、Javaメソッドmaxの呼び出しがあるが、Javaメソッドmaxの定義には@Nativeアノテーションが付与されているため、maxの呼び出し部分をCコードへ変換するときに、@Nativeアノテーションで与えられたC関数定義が適用される。

具体的には、@Nativeアノテーションの引数をfunction bodyとするC関数が出力され、maxメソッドの呼び出し部分は、この出力されたC関数を呼び出すようになる。

```Java
package sample;

import javassist.offload.Foreign;
import javassist.offload.Native;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;


public class AnnotationNative {
	
	@Native("return v1 > v2 ? v1 : v2;")
	public static float max(float a, float b) {
		return 0.0f;
	}
	
    public static void main(String[] args) throws Exception {
    	
    	new StdDriver().invoke(() -> {
    		Util.printer.p("Hello, Annotation Native").ln();
    		Util.printer.p("max(2.0f, 3.14f) = ").p(max(2.0f, 3.14f)).ln();
    	});    	
    }
	
}
```

その結果、このプログラムを実行した時の出力は次のようになる。

```
Hello, Annotation Native
max(2.0f, 3.14f) = 3.14
```

変換後のCコードbytespresso.cを調べると、次に示すようにC関数定義が新たに作られていることが確認できる。

```C
/* native */ float AnnotationNative_max_9(float v1, float v2) {
  return v1 > v2 ? v1 : v2;
}
```

変換対象となっていたJava Lambda式は次のようなCコードに変換されている。max関数の呼び出しが、@Nativeで作られたC関数の呼び出しになっている。

```C
void AnnotationNative_lambda_0_2() {

  Util_Printer_ln_7(Util_Printer_p_3_0(&gvar0, ((struct java_string*)"\2\0\0\0\30\0\0\0" "Hello, Annotation Native")));
  Util_Printer_ln_7(Util_Printer_p_10(Util_Printer_p_3_0(&gvar0, ((struct java_string*)"\2\0\0\0\23\0\0\0" "max(2.0f, 3.14f) = ")), AnnotationNative_max_9(2.0f, 3.14f)));
  return ;

}
```

