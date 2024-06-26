package com.redes;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Maquina2 {
    public static void main(String[] args) {

        Semaphore sem = new Semaphore(3);

        String ipMaquina = "localhost";

        EnviaDados ed1 = new EnviaDados(sem, "envia", ipMaquina);
        EnviaDados ed2 = new EnviaDados(sem, "ack", ipMaquina);
        EnviaDados t = new EnviaDados(sem, "timeout", ipMaquina);

        ed2.start();
        t.start();
        ed1.start();

        try {

            ed1.join();
            ed2.join();
            t.join();

        } catch (InterruptedException ex) {
            Logger.getLogger(Maquina2.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}