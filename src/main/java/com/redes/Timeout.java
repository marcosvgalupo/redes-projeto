package com.redes;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

class Timeout{

    private long milliseconds;
    private Timer timer;

    private EnviaDados sender;
    private final Map<Integer, int[]> copiaDados = new HashMap<>();
    private final ConcurrentHashMap<Integer, TimerTask> tasks = new ConcurrentHashMap<>();


    public Timeout(){
        this.timer = new Timer();
        this.milliseconds = 1;
    }

    public void setMilliseconds(long m){
        this.milliseconds = m;
    }

    public long getMilliseconds(){
        return this.milliseconds;
    }

    public ConcurrentHashMap<Integer, TimerTask> getTasks(){
        return tasks;
    }


    public synchronized void startTimer(TimerTask task, int seqNum,long millisec) {
        stopTimer(seqNum);

        tasks.put(seqNum, task);

        timer.schedule(task, milliseconds, millisec);
    }

    public synchronized void stopTimer(int seqNum) {
        TimerTask task = tasks.remove(seqNum);
        if (task != null) {
            task.cancel();
        }
    }
}
