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

コールバックするJVMは、StdDriverを利用している時は、ホストJavaプログラムを実行するJVMである。一方、MPIDriverを利用している場合は、MPI Processから起動されるノードローカルなJVMになる。

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

実行結果は次のようになる

```
PID of the host JVM: 58162
PID of Translated C 58166
PID of @Remote JVM 58162
```

hostプログラムを動かしているJVMと@RemoteでコールバックされるJVMが同じプロセスで、変換されたCコードだけが別のプロセスで実行される。

上のプログラムをMPIDriverを利用するように変更したのが以下のプログラムである。

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

上記のコードの実行結果は以下のようになる
```
PID of the host JVM: 58189
PID of Translated C 58205
PID of Translated C 58206
PID of @Remote JVM 
PID of Translated C 58207
PID of @Remote JVM 
PID of Translated C 58208
PID of @Remote JVM 
PID of @Remote JVM 
```

