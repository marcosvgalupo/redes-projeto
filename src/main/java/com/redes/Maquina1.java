package com.redes;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Maquina1{
    public static void main(String[] args) {

        String ipMaquina = "localhost";

        RecebeDados rd = new RecebeDados(ipMaquina);
        rd.start();

        try {

            rd.join();

        } catch (InterruptedException ex) {
            Logger.getLogger(Maquina1.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}