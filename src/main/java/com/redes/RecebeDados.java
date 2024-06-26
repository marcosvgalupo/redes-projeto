/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redes;

/**
 * @author flavio
 */

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecebeDados extends Thread {

    private final int portaLocalReceber = 2001;
    private final int portaLocalEnviar = 2002;
    private final int portaDestino = 2003;
    private static String ipMaquina;

    private final HashMap<Integer, Boolean> acksSent= new HashMap<>();
    private final TerminalColors colors;
    private boolean packetWasLost;

    private final Queue<Long> pacotesPerdidos = new LinkedList<>();

    private long ultimoNSValido = -1;
    private long numeroSequenciaEsperado = 0;

    public RecebeDados(String ipMaquina) {
        this.colors = null;
        this.ultimoNSValido = 0;
        this.packetWasLost = false;
        this.ipMaquina = ipMaquina;
    }

    public void incrementaNumeroSequenciaEsperado(){
        if(numeroSequenciaEsperado == Long.MAX_VALUE)
            numeroSequenciaEsperado = 0;
        else
            numeroSequenciaEsperado++;
    }

    private boolean pacoteFoiPerdido(){
        var random = new Random();
        float probabilidadeDePerda = random.nextFloat();

        return probabilidadeDePerda <= 0.6;
    }

    private void enviaAck(boolean fim, long seq) {

        try {

            InetAddress address = InetAddress.getByName(ipMaquina);
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnviar)) {
                ByteBuffer buffer = ByteBuffer.allocate(8);
                buffer.putLong(fim ? -1 : seq);
                byte[] sendData = buffer.array();

                DatagramPacket packet = new DatagramPacket(
                        sendData, sendData.length, address, portaDestino);
                datagramSocket.send(packet);
            }
        } catch (IOException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        try {
            DatagramSocket serverSocket = new DatagramSocket(portaLocalReceber);
            byte[] receiveData = new byte[2808];
            try (FileOutputStream fileOutput = new FileOutputStream("saida")) {
                boolean fim = false;
                while (!fim) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);



                    byte[] tmp = receivePacket.getData();
                    long numeroSequencia = ((long)(tmp[0] & 0xff) << 56) + ((long)(tmp[1] & 0xff) << 48) + ((long)(tmp[2] & 0xff) << 40) +
                            ((long)(tmp[3] & 0xff) << 32) + ((long)(tmp[4] & 0xff) << 24) + ((long)(tmp[5] & 0xff) << 16) +
                            ((long)(tmp[6] & 0xff) << 8) + ((long)(tmp[7] & 0xff));

                    System.out.println("numero de sequencia atual recebe dados: " + numeroSequencia);
                    System.out.println("numero de sequencia esperado recebe dados: " + numeroSequenciaEsperado);

                    //probabilidade de 60% de perder
                    boolean perdido = pacoteFoiPerdido();
                    if(perdido && numeroSequencia != ultimoNSValido) {
                        System.out.println(colors.CYAN + "Pacote [" + numeroSequencia +"] perdido!!"+ colors.RESET);
                        pacotesPerdidos.add(numeroSequencia);
                        packetWasLost = true;
                        enviaAck(false, ultimoNSValido);
                        continue;
                    }
                    if(numeroSequencia == numeroSequenciaEsperado){
                        packetWasLost = false;
                        ultimoNSValido = numeroSequencia;
                    }
                    else if(packetWasLost){
                        enviaAck(false, ultimoNSValido);
                        continue;
                    }

                    System.out.println(colors.BLUE+"[Pacote recebido "+ numeroSequencia +"]." + colors.RESET);

                    if(!packetWasLost) {

                        for (int i = 8; i < tmp.length; i = i + 8) {
                            long dados = ((long)(tmp[i] & 0xff) << 56) + ((long)(tmp[i + 1] & 0xff) << 48) +
                                    ((long)(tmp[i + 2] & 0xff) << 40) + ((long)(tmp[i + 3] & 0xff) << 32) +
                                    ((long)(tmp[i + 4] & 0xff) << 24) + ((long)(tmp[i + 5] & 0xff) << 16) +
                                    ((long)(tmp[i + 6] & 0xff) << 8) + ((long)(tmp[i + 7] & 0xff));


                            if (dados == -1) {
                                fim = true;
                                //enviaAck(fim, -1);
                                break;
                            }
                            fileOutput.write((int) dados);
                        }

                        enviaAck(fim, numeroSequenciaEsperado);
                        incrementaNumeroSequenciaEsperado();
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Excecao: " + e.getMessage());
        }
    }
}
