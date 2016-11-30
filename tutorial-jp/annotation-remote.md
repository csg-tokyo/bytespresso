Bytespresso: Annotation @Remote
==

仕様
--
- 対象: staticメソッド
	- 引数の型及び返り値の型は、primitiveな型またはその配列に限られる
- 記法: @Remote


説明
--
@Remoteアノテーションは、変換後のCコードからJavaコードを走らせるための仕組みである。Cに変換するJava Lambda式には記述上の制約が多い為、時には一部の処理については普通のJavaのコードで実行したいという要求がある。@Remoteは、JVMへのコールバックを実現するものである。

コールバックするJVMは、StdDriverを利用している時は、ホストJavaプログラムを実行するJVMである。一方、MPIDriverを利用している場合は、各MPI Processから別途起動されるJVMで実行される。この場合、呼び出し元のJVMに処理を投げるわけではないため、「コールバック」という表現よりも「リモートメソッド呼び出し」という表現がより適切である。

利用例
--

以下のプログラムは StdDriverを利用したとき、ホストJavaプログラム、変換後のCコード、CコードからコールバックされたJVMそれぞれのプロセスIDを調べるものである。

```Java
package sample;

import javassist.offload.Remote;
import javassist.offload.Foreign;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;

class CustomizedDriver extends StdDriver {

	@Override public String preamble() {
        return super.preamble() + "#include <unistd.h>\n";
    }
}

public class AnnotationRemote {
			
	public static final String PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	
	@Remote public static String getJvmPID() {
		return PID;
	}
	
	@Foreign public static int getpid() {
		return 0;
	}
		
    public static void main(String[] args) throws Exception {
    	System.out.println("PID of the host JVM: " + getJvmPID());
    	new CustomizedDriver().invoke(() -> {
    		Util.printer.p("PID of Translated C ").p(getpid()).ln();
    		Util.printer.p("PID of @Remote JVM ").p(getJvmPID()).ln();
    	});
    }
}
```

実行結果は次のようになる。`getJvmPID()`メソッドはホストJavaプログラムを実行中のJVMで実行される。すなわち、hostプログラムを動かしているJVMと@RemoteでコールバックされるJVMが同じPIDで、変換されたCコードだけが別のPIDで実行される。

```
PID of the host JVM: 58162
PID of Translated C 58166
PID of @Remote JVM 58162
```

先のサンプルプログラムをMPIDriverを利用するように変更したのが以下のプログラムである。

```Java
package sample;

import javassist.offload.Remote;
import javassist.offload.Foreign;
import javassist.offload.javatoc.Callback;
import javassist.offload.javatoc.StdDriver;
import javassist.offload.lib.Util;
import javassist.offload.lib.MPI;
import javassist.offload.lib.MPIDriver;

class CustomizedMPIDriver extends MPIDriver {
	
	public CustomizedMPIDriver(int num) {
		super(num);
	}

	@Override public String preamble() {
        return super.preamble() + "#include <unistd.h>\n";
    }
}

public class AnnotationRemoteMPI {

	public static final String PID = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
	
	@Remote public static String getJvmPID() {
		return PID;
	}
	
	@Foreign public static int getpid() {
		return 0;
	}
			
    public static void main(String[] args) throws Exception {
    	System.out.println("PID of the host JVM: " + getJvmPID());
    	new CustomizedMPIDriver(4).invoke(() -> {
    		Util.printer.p("PID of Translated C ").p(getpid()).ln();
    		Util.printer.p("PID of @Remote JVM ").p(getJvmPID()).ln();
    	});
    }	
}
```

上記のプログラムの実行結果は以下のようになる。MPIDriverでの実行の場合、`getJvmPID()`メソッドはMPIプロセスごとに起動されるJVMで実行される。そのため、PIDは全て違う結果となる。

```
PID of the host JVM: 3694
PID of Translated C 3710
PID of Translated C 3712
PID of Translated C 3713
PID of Translated C 3711
PID of @Remote JVM 3714
PID of @Remote JVM 3717
PID of @Remote JVM 3715
PID of @Remote JVM 3716
```

