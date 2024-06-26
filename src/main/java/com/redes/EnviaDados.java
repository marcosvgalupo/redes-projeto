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
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;

public class EnviaDados extends Thread {

    private static final int portaLocalEnvio = 2000;
    private static final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    private static final HashMap<Integer, byte[]> bufferPacotes = new HashMap<>();
    private static final Stack<Integer> acksDuplicados = new Stack<Integer>();
    private static Timeout timeout;

    static Semaphore sem;
    private final String funcao;
    private static boolean precisaRetransmitir = false;
    private static int primeiroPacoteARetransmitir = 0;


    private static int numeroSequencia = 0;
    private static int ackEsperado = 0;
    private static boolean packetWasLost = false;


    private static final TerminalColors colors = null;


    public EnviaDados(Semaphore sem, String funcao) {
        super(funcao);
        this.sem = sem;
        this.funcao = funcao;
    }

    private static synchronized void incrementaNumeroSequencia() {
        numeroSequencia++;
    }

    private synchronized void setNumeroSequencia(int numero) {
        EnviaDados.numeroSequencia = numero;
    }

    private static synchronized void setAckEsperado(int ack) {EnviaDados.ackEsperado = ack;}

    private static synchronized void tresAcksDuplicados(int numACK) throws InterruptedException {
        if (numACK != ackEsperado && numACK != -1) {

            System.out.println(colors.MAGENTA + "ACK " + numACK + " DUPLICADO" + colors.RESET);

            if(acksDuplicados.size() == 2){
                System.out.println(colors.RED + "REENVIO DE PACOTE POR TRÊS ACKS [" + numACK+ "] DUPLICADOS" + colors.RESET);

                setAckEsperado(numACK+1);
                packetWasLost = true;
                precisaRetransmitir = true;
                primeiroPacoteARetransmitir = numACK+1;

                while(!acksDuplicados.isEmpty()){
                    acksDuplicados.pop();
                }
            }
        }
    }

    public String getFuncao() {return funcao;}


    private static void enviaPct(int[] dados, boolean ehRetransmissao) {

        //converte int[] para byte[]
        ByteBuffer byteBuffer = ByteBuffer.allocate(dados.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(dados);

        byte[] buffer = byteBuffer.array();
        bufferPacotes.put(dados[0], buffer);

        try {

            System.out.println("Semaforo: " + sem.availablePermits());

            System.out.println("Semaforo: " + sem.availablePermits());

            InetAddress address = InetAddress.getByName("localhost");
            try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                if(precisaRetransmitir){
                    sem.acquire();
                    for(int i = primeiroPacoteARetransmitir; i <= numeroSequencia; i ++){

                        if(bufferPacotes.get(i) != null) {
                            System.out.println(colors.RED + "RETRANSMITINDO: " + i + colors.RESET);

                            byte[] reenviar = bufferPacotes.get(i);

                            System.out.println("numseq: " + i + "---- dados[0]");

                            DatagramPacket packet = new DatagramPacket(reenviar, reenviar.length, address, portaDestino);
                            System.out.println(colors.GREEN + "Pacote " + i + " enviado." + colors.RESET);
                            datagramSocket.send(packet);
                        }
                    }
                    precisaRetransmitir = false;
                }
                else {
                    sem.acquire();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, portaDestino);
                    System.out.println(colors.GREEN + "Pacote " + dados[0] + " enviado." + colors.RESET);
                    datagramSocket.send(packet);
                }
            }
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

                            enviaPct(dados, false);
                            cont = 1; //reseta para o pŕoximo pacote
                        }
                    }
                    //ultimo pacote eh preenchido com
                    //-1 ate o fim, indicando que acabou
                    //o envio dos dados.
                    for (int i = cont; i < 351; i++)
                        dados[i] = -1;

                    dados[0] = numeroSequencia;
                    enviaPct(dados, false);
                } catch (IOException e) {
                    System.out.println("Error message: " + e.getMessage());
                }
                break;
            case "ack":
                try {
                    System.out.println("coringuei");
                    DatagramSocket serverSocket = new DatagramSocket(portaLocalRecebimento);
                    System.out.println("vo nada");
                    byte[] receiveData = new byte[4];
                    int numACK = 0;
                    while (numACK != -1) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        var b = ByteBuffer.wrap(receivePacket.getData());
                        numACK = b.getInt();

                        System.out.println("ackzin dos cria: " + numACK);
                        tresAcksDuplicados(numACK);
                        if(acksDuplicados.isEmpty()) {
                            acksDuplicados.push(numACK);
                            ackEsperado++; // 1
                        }
                        else if(numACK == acksDuplicados.pop()){
                            acksDuplicados.push(numACK);
                            acksDuplicados.push(numACK);
                        }
                        else{
                            acksDuplicados.push(numACK);
                            ackEsperado++;
                        }
                        //tresAcksDuplicados(numACK);


                        System.out.println("ack esperado: " + ackEsperado);
                        System.out.println("resultado ack: "+ numeroSequencia);
                        System.out.println("numero sequencia atual: "+ numeroSequencia);
                        //System.out.println("PACOTE ATUAL: " + numeroSequencia + "------ timeout hashmap: " + timeout.getTasks());
                       // timeout.stopTimer(numACK);
                        if(numACK == -1)
                            System.out.println(colors.YELLOW + "ACK " + (numeroSequencia-1) + " recebido." + colors.RESET);
                        else
                            System.out.println(colors.YELLOW + "ACK " + numACK + " recebido." + colors.RESET);

                        if(numACK == -1){
                            serverSocket.close();
                            break;
                        }
                        sem.release();
                    }
                } catch (IOException e) {
                    System.out.println("Excecao: " + e.getMessage());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                break;
            //TODO timeout
            case "timeout":
//                timeout = new Timeout();
//                while(numeroSequencia != -1){
//                    if(timeout.getTasks().get(numeroSequencia) == null){
//                        timeout.startTimer(new TimerTask() {
//                            @Override
//                            public void run() {
//                                System.out.println(colors.RED + "TIMEOUT: Pacote " + numeroSequencia + colors.RESET);
//                                try {
//                                    retransmitirPacotes(numeroSequencia);
//                                } catch (InterruptedException e) {
//                                    throw new RuntimeException(e);
//                                }
//                            }
//                        }, numeroSequencia, timeout.getMilliseconds());
//                    }
//                }
            default:

                break;
        }

    }
}
