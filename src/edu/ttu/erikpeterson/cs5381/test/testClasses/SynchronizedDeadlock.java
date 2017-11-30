package edu.ttu.erikpeterson.cs5381.test.testClasses;

public class SynchronizedDeadlock {
    private final String string1 = "String1";
    private final String string2 = "String2";

    private Thread thread1 = new Thread() {
        public void run()
        {
            synchronized(string1)
            {
                synchronized (string2)
                {
                    System.out.println("1 then 2");
                }
            }
        }
    };

    private Thread thread2 = new Thread(() -> {
        this.myMethod();
    });

    private void myMethod()
    {
        lockInOtherOrder();
    }

    private void lockInOtherOrder()
    {
        synchronized(string2)
        {
            synchronized (string1)
            {
                System.out.println("2 then 1");
            }
        }
    }

    /**
     * This one should NOT be marked, since we're unlocking string2 before locking string1
     */
    private Thread thread3 = new Thread(() -> {
        synchronized(string2)
        {
            System.out.println("2 only");
        }

        synchronized(string1)
        {
            System.out.println("1 only");
        }
    });
}
