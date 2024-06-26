/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redes;

/**
 * @author flavio
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Timer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;

public class EnviaDados extends Thread {

    private final int portaLocalEnvio = 2000;
    private final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    private final long TIMEOUT = 1000;
    Semaphore sem;
    private final String funcao;


    private int numeroTotalDePacotes = 0;

    private int numeroSequencia = 0;
    private Map<Integer, Timer> timers = new HashMap<>();


    public EnviaDados(Semaphore sem, String funcao) {
        super(funcao);
        this.sem = sem;
        this.funcao = funcao;
    }

    public String getFuncao() {
        return funcao;
    }

    private synchronized void startTimer(int seqNum, int[] dados) {
        Timer timer = new Timer();
        timer.schedule(new RetransmissionTask(this, dados), TIMEOUT);
        timers.put(seqNum, timer);
    }

    private synchronized void stopTimer(int seqNum) {
        Timer timer = timers.remove(seqNum);
        if (timer != null) {
            timer.cancel();
        }
    }

    public synchronized void retransmit(int[] dados) {
        System.out.println("Retransmitindo pacote: " + dados[0]);
        enviaPct(dados);
    }

    private void enviaPct(int[] dados) {

        //converte int[] para byte[]
        ByteBuffer byteBuffer = ByteBuffer.allocate(dados.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(dados);

        byte[] buffer = byteBuffer.array();

        try {
            System.out.println("Semaforo: " + sem.availablePermits());
            sem.acquire();
            System.out.println("Semaforo: " + sem.availablePermits());

            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                DatagramPacket packet = new DatagramPacket(
                        buffer, buffer.length, address, portaDestino);

                datagramSocket.send(packet);
            }

            System.out.println("Envio feito.");

            startTimer(dados[0],dados);

        } catch (SocketException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        switch (this.getFuncao()) {
            case "envia":

                int[] dados = new int[351]; // estrutura: [numeroSequencia, dados....]
                //contador, para gerar pacotes com 1404 Bytes de tamanho (1400 bytes de dados e 1 do numero de sequencia)
                //como cada int ocupa 4 Bytes, estamos lendo blocos com 350 bytes e 1 byte para o número de sequência

                int cont = 1; // pular a posição do número de sequẽncia

                try (FileInputStream fileInput = new FileInputStream("entrada");) {
                    int lido;
                    while ((lido = fileInput.read()) != -1) {
                        dados[cont] = lido;
                        cont++;
                        if (cont == 351) {
                            //envia pacotes a cada 350 int's lidos.
                            //ou seja, 1400 Bytes de dados.
                            dados[0] = numeroSequencia;
                            numeroSequencia++;
                            enviaPct(dados);
                            cont = 1; //reseta para o pŕoximo pacote
                        }
                    }

                    //ultimo pacote eh preenchido com
                    //-1 ate o fim, indicando que acabou
                    //o envio dos dados.
                    for (int i = cont; i < 351; i++)
                        dados[i] = -1;
                    enviaPct(dados);
                } catch (IOException e) {
                    System.out.println("Error message: " + e.getMessage());
                }
                break;
            case "ack":
                try {
                    DatagramSocket serverSocket = new DatagramSocket(portaLocalRecebimento);
                    byte[] receiveData = new byte[1];
                    String retorno = "";
                    while (!retorno.equals("F")) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        retorno = new String(receivePacket.getData());
                        int numeroDeSequencia = retorno.charAt(0);
                        System.out.println("Ack recebido " + retorno + ".");
                        sem.release();
                        stopTimer(numeroDeSequencia);
                        numeroTotalDePacotes++;
                    }
                    System.out.println("Numero total de pacotes: " + numeroTotalDePacotes);
                } catch (IOException e) {
                    System.out.println("Excecao: " + e.getMessage());
                }
                break;
            //TODO timer
            default:

                break;
        }

    }
}
