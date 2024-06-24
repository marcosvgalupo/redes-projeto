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
import java.util.HashMap;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecebeDados extends Thread {

    private final int portaLocalReceber = 2001;
    private final int portaLocalEnviar = 2002;
    private final int portaDestino = 2003;
    private final HashMap<Integer, Boolean> acksSent= new HashMap<>();


    private int numeroSequenciaEsperado = 0;


    private boolean pacoteFoiPerdido(){
        var random = new Random();
        float probabilidadeDePerda = random.nextFloat();

        return probabilidadeDePerda <= 0.6;
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

                    System.out.println("pacote recebido.");

                    byte[] tmp = receivePacket.getData();
                    int numeroSequencia = ((tmp[0] & 0xff) << 24) + ((tmp[1] & 0xff) << 16) + ((tmp[2] & 0xff) << 8) + ((tmp[3] & 0xff));

//                    System.out.println("Numero de sequencia atual: " + numeroSequencia);
//                    System.out.println("numero de sequencia esperado: " + numeroSequenciaEsperado);
                    boolean perdido = pacoteFoiPerdido();
                    if(perdido){

                    }
                    //probabilidade de 60% de perder
                    //gero um numero aleatorio contido entre [0,1]
                    //se numero cair no intervalo [0, 0,6)
                    //significa perda, logo, você não envia ACK
                    //para esse pacote, e não escreve ele no arquivo saida.
                    //se o numero cair no intervalo [0,6, 1,0]
                    //assume-se o recebimento com sucesso.

                    if(numeroSequencia != numeroSequenciaEsperado){
                        continue;
                    }

//                    if(numeroSequencia != numeroSequenciaEsperado){
//                        System.out.println(numeroSequencia);
//                        System.out.println(numeroSequenciaEsperado);
//                        System.out.println("Pacote fora de ordem!!!");
//                        if(!acksSent.containsKey(numeroSequencia)){
//                            System.out.println("entrou");
//                            enviaAck(false, numeroSequencia);
//                        }
//                        enviaAck(false, numeroSequencia);
//                    }
                    for (int i = 4; i < tmp.length; i = i + 4) {
                            int dados = ((tmp[i] & 0xff) << 24) + ((tmp[i + 1] & 0xff) << 16) + ((tmp[i + 2] & 0xff) << 8) + ((tmp[i + 3] & 0xff));

                            if (dados == -1) {
                                fim = true;
                                break;
                            }
                            fileOutput.write(dados);
                    }
                    enviaAck(fim, numeroSequenciaEsperado);
                    numeroSequenciaEsperado++;
                }
            }
        } catch (IOException e) {
            System.out.println("Excecao: " + e.getMessage());
        }
    }
}
