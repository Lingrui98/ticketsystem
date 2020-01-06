1. 单线程正确性测试
a) 用当前版的Trace.java替换原有的Trace.java
b) 编译后生成Trace文件，命名为trace
c) 将verify.jar放在trace文件同一目录
d) 执行 java -jar verify.jar
e) 如果输出只有 Trace Verification Finished，则通过测试。否则说明有错。

2. 多线程并发正确性测试
a) 修改当前Trace.java的参数，把线程数设成8或更多，其他参数也可增大
b) 除了保留输出System.out.println("ErrOfRefund");语句，其他输出全注释掉
c) 编译执行Trace
d) 如果输出ErrOfRefund，说明有并发错误