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
import java.util.HashMap;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;

public class EnviaDados extends Thread {

    private final int portaLocalEnvio = 2000;
    private final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    private final Timeout timeout;
    private HashMap<Integer, int[]> bufferPacotes;

    Semaphore sem;
    private final String funcao;


    private int numeroTotalDePacotes = 0;
    private static int numeroSequencia = 0;
    private static int numeroSequenciaPacoteRetransmitido = 0;

    private final TerminalColors colors;

    public int getNumeroTotalDePacotes(){
        return numeroTotalDePacotes;
    }

    public EnviaDados(Semaphore sem, String funcao, Timeout timeout) {
        super(funcao);
        this.sem = sem;
        this.funcao = funcao;
        this.timeout = timeout;
        this.bufferPacotes = new HashMap<>();
        this.colors = null;
    }

    private synchronized void incrementaNumeroSequencia() {
        numeroSequencia++;
    }

    private synchronized void setPacoteRetransmitido(int seq) {
        numeroSequenciaPacoteRetransmitido = seq;
    }

    private synchronized void setNumeroSequencia(int numeroSequencia) {
        EnviaDados.numeroSequencia = numeroSequencia;
    }

    public synchronized void retransmitirPacotes(int seq) throws InterruptedException {
        sem.acquire();
        if(bufferPacotes.get(seq) != null){
            timeout.setMilliseconds(timeout.getMilliseconds() * 2);

            for(int i = seq; i < numeroSequencia; i ++){
                System.out.println(colors.RED + "RETRANSMITINDO: " + i + colors.RESET);
                int dadosRetransmitir[] = bufferPacotes.get(i);
                enviaPct(dadosRetransmitir);
            }
        }
        sem.release();
    }

    public synchronized void retransmitir2(int[] dados){
        enviaPct(dados);
        setNumeroSequencia(dados[0]);
        timeout.setMilliseconds(timeout.getMilliseconds() * 2);
    }

    public String getFuncao() {
        return funcao;
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


                System.out.println(colors.GREEN + "Pacote " + dados[0] + " enviado." + colors.RESET);
                datagramSocket.send(packet);


            }
            timeout.startTimer(new TimerTask() {
                @Override
                public void run() {
                    System.out.println(colors.RED + "TIMEOUT: Pacote " + dados[0] + colors.RESET);
                    setPacoteRetransmitido(dados[0]);
                    try {
                        retransmitirPacotes(dados[0]);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }, dados[0], timeout.getMilliseconds());
            incrementaNumeroSequencia();
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

                            bufferPacotes.put(dados[0], dados.clone());

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
                    byte[] receiveData = new byte[4];
                    int numSeq = 0;
                    while (numSeq != -1) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        var b = ByteBuffer.wrap(receivePacket.getData());
                        numSeq = b.getInt();
                        if(numSeq == numeroSequenciaPacoteRetransmitido){
                            //
                        }
                        timeout.stopTimer(numSeq);
                        System.out.println(colors.YELLOW + "ACK " + numSeq + " recebido." + colors.RESET);
                        sem.release();
                        numeroTotalDePacotes++;
                    }
                    //System.out.println("Numero total de pacotes: " + numeroTotalDePacotes);
                } catch (IOException e) {
                    System.out.println("Excecao: " + e.getMessage());
                }
                break;
            //TODO timeout
            default:

                break;
        }

    }
}
