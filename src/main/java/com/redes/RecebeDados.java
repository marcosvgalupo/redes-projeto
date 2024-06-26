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
    private String ipMaquina;

    private final HashMap<Integer, Boolean> acksSent= new HashMap<>();
    private final Queue<Integer> pacotesPerdidos= new LinkedList<>();
    private final TerminalColors colors;
    private boolean packetWasLost;


    private long ultimoNSValido;
    private long numeroSequenciaEsperado = 0;
    private long ultimoPacoteEnviado = 0;

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

        return probabilidadeDePerda <= 0.2;
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
                System.out.println("seq seq seq: " + seq);
                //acksSent.put(seq,true);
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
            byte[] receiveData = new byte[2808];
            try (FileOutputStream fileOutput = new FileOutputStream("saida")) {
                boolean fim = false;
                while (!fim) {
                    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                    serverSocket.receive(receivePacket);



                    byte[] tmp = receivePacket.getData();
                    long numeroSequencia = ((long)(tmp[0] & 0xff) << 56) +
                            ((long)(tmp[1] & 0xff) << 48) +
                            ((long)(tmp[2] & 0xff) << 40) +
                            ((long)(tmp[3] & 0xff) << 32) +
                            ((long)(tmp[4] & 0xff) << 24) +
                            ((long)(tmp[5] & 0xff) << 16) +
                            ((long)(tmp[6] & 0xff) << 8) +
                            ((long)(tmp[7] & 0xff));



                    //probabilidade de 60% de perder
//                    boolean perdido = pacoteFoiPerdido();
//                    if(perdido && numeroSequencia != 0 && !packetWasLost) {
//                        System.out.println(colors.CYAN + "Pacote [" + numeroSequencia +"] perdido!!"+ colors.RESET);
//
//
//                        pacotesPerdidos.add(numeroSequencia);
//                        packetWasLost = true;
//                        enviaAck(false, ultimoNSValido);
//                        enviaAck(false, ultimoNSValido);
//                        continue;
//                    }
                    if(packetWasLost && acksSent.get(numeroSequencia) != null && numeroSequencia == numeroSequenciaEsperado){
                        System.out.println("oi");
                    }
                    else if(numeroSequencia == numeroSequenciaEsperado){
                        System.out.println("numero seq: " + numeroSequencia);
                        System.out.println("esperadasso: " + numeroSequenciaEsperado);
                        packetWasLost = false;
                    }
                    System.out.println(colors.BLUE+"[Pacote recebido "+ numeroSequencia +"]." + colors.RESET);

                    if(!packetWasLost) {
                        ultimoNSValido = numeroSequencia;
                        System.out.println("novo ultimo num sequencia: " + ultimoNSValido);
                        for (int i = 8; i < tmp.length; i = i + 8) {
                            long dados = ((long)(tmp[i] & 0xff) << 56) +
                                    ((long)(tmp[i + 1] & 0xff) << 48) +
                                    ((long)(tmp[i + 2] & 0xff) << 40) +
                                    ((long)(tmp[i + 3] & 0xff) << 32) +
                                    ((long)(tmp[i + 4] & 0xff) << 24) +
                                    ((long)(tmp[i + 5] & 0xff) << 16) +
                                    ((long)(tmp[i + 6] & 0xff) << 8) +
                                    ((long)(tmp[i + 7] & 0xff));


                            if (dados == -1) {
                                fim = true;
                                enviaAck(fim, -1);
                                break;
                            }
                            fileOutput.write((int) dados);
                        }
                        System.out.println("enviando ack para o num: " + numeroSequenciaEsperado);
                        enviaAck(fim, numeroSequenciaEsperado);
                        incrementaNumeroSequenciaEsperado();
                        //acksSent.put(numeroSequencia, true);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Excecao: " + e.getMessage());
        }
    }
}
