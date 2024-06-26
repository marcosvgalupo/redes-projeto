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
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecebeDados extends Thread {

    private final int portaLocalReceber = 2001;
    private final int portaLocalEnviar = 2002;
    private final int portaDestino = 2003;
    private final HashMap<Integer, Boolean> acksSent= new HashMap<>();
    private final Queue<Integer> pacotesPerdidos= new LinkedList<>();
    private final TerminalColors colors;
    private boolean packetWasLost;


    private int ultimoNumeroSequencia;
    private int numeroSequenciaEsperado = 0;

    public RecebeDados() {
        this.colors = null;
        this.ultimoNumeroSequencia = 0;
        this.packetWasLost = false;
    }


    private boolean pacoteFoiPerdido(){
        var random = new Random();
        float probabilidadeDePerda = random.nextFloat();

        return probabilidadeDePerda <= 0.2;
    }

    private void enviaAck(boolean fim, int seq) {

        try {
            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnviar)) {
                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.putInt(fim ? -1 : seq);
                byte[] sendData = buffer.array();

                DatagramPacket packet = new DatagramPacket(
                        sendData, sendData.length, address, portaDestino);
                datagramSocket.send(packet);
                System.out.println("seq seq seq: " + seq);
                acksSent.put(seq,true);
            }
        } catch (SocketException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(RecebeDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        try {
            DatagramSocket serverSocket = new DatagramSocket(portaLocalReceber);
            byte[] receiveData = new byte[1404];
            try (FileOutputStream fileOutput = new FileOutputStream("saida")) {
                boolean fim = false;
                while (!fim) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);



                    byte[] tmp = receivePacket.getData();
                    int numeroSequencia = ((tmp[0] & 0xff) << 24) + ((tmp[1] & 0xff) << 16) + ((tmp[2] & 0xff) << 8) + ((tmp[3] & 0xff));

//                    System.out.println("esprrrrrr: " + numeroSequenciaEsperado);
//                    if(numeroSequencia != numeroSequenciaEsperado){
//                        enviaAck(false, numeroSequenciaEsperado);
//                        continue;
//                    }

                    //probabilidade de 60% de perder
                    boolean perdido = pacoteFoiPerdido();
                    if(perdido && numeroSequencia != 0) {
                        System.out.println(colors.CYAN + "Pacote [" + numeroSequencia +"] perdido!!"+ colors.RESET);
                        System.out.println("quando ele foi perdido numseq: " + numeroSequencia);
                        System.out.println("quando ele foi perdido numseqEsp: " + numeroSequenciaEsperado);
                        System.out.println("quando ele foi perdido numseqUlt: " + ultimoNumeroSequencia);

                        pacotesPerdidos.add(numeroSequencia);
                        packetWasLost = true;
                        enviaAck(false, ultimoNumeroSequencia);
                        enviaAck(false, ultimoNumeroSequencia);
                        //numeroSequenciaEsperado = ultimoNumeroSequencia;
                        continue;
                    }
                    if(packetWasLost && acksSent.get(numeroSequencia) != null && numeroSequencia == numeroSequenciaEsperado){
                        System.out.println("oi");
                    }
                    else if(numeroSequencia == numeroSequenciaEsperado){
                        packetWasLost = false;
                    }
                    System.out.println(colors.BLUE+"[Pacote recebido "+ numeroSequencia +"]." + colors.RESET);
                    System.out.println("ultimo numero de sequencia: " + ultimoNumeroSequencia);
                    System.out.println("numero de sequencia esperado: " + numeroSequenciaEsperado);
                    System.out.println("ack recebido atual: " + numeroSequencia);
                    if(!packetWasLost) {
                        ultimoNumeroSequencia = numeroSequencia;
                        System.out.println("novo ultimo num sequencia: " + ultimoNumeroSequencia);
                        for (int i = 4; i < tmp.length; i = i + 4) {
                            int dados = ((tmp[i] & 0xff) << 24) + ((tmp[i + 1] & 0xff) << 16) + ((tmp[i + 2] & 0xff) << 8) + ((tmp[i + 3] & 0xff));

                            if (dados == -1) {
                                fim = true;
                                enviaAck(fim, -1);
                                break;
                            }
                            fileOutput.write(dados);
                        }
                        System.out.println("enviando ack para o num: " + numeroSequenciaEsperado);
                        enviaAck(fim, numeroSequenciaEsperado);
                        acksSent.put(numeroSequencia, true);
                        numeroSequenciaEsperado++;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Excecao: " + e.getMessage());
        }
    }
}
