package com.redes;

import java.util.TimerTask;

class RetransmissionTask extends TimerTask {
    private final EnviaDados sender;
    private final int[] packet;

    public RetransmissionTask(EnviaDados sender, int[] packet) {
        this.sender = sender;
        this.packet = packet;
    }

    @Override
    public void run() {
        sender.retransmit(packet);
    }
}
