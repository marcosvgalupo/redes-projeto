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
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;

public class EnviaDados extends Thread {

    private static final int portaLocalEnvio = 2000;
    private static final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    private String ipMaquina;


    private static final HashMap<Long, byte[]> bufferPacotes = new HashMap<>();
    private static final Stack<Integer> acksDuplicados = new Stack<Integer>();
    private static Timeout timeout;

    private static final TerminalColors colors = null;

    static Semaphore sem;
    private final String funcao;
    private static volatile boolean precisaRetransmitir = false;
    private static long primeiroPacoteARetransmitir = 0;


    private static volatile long numeroSequencia = 0;
    private static volatile long ackEsperado = 0;
    private static volatile int numeroAcksDuplicados = 0;
    private static volatile long ackAnterior = -1;



    public EnviaDados(Semaphore sem, String funcao, String ipMaquina) {
        super(funcao);
        this.sem = sem;
        this.funcao = funcao;
        this.ipMaquina = ipMaquina;
    }

    private static synchronized void incrementaNumeroSequencia() {
        if(numeroSequencia == Long.MAX_VALUE)
            numeroSequencia = 0;
        else
            numeroSequencia++;
    }

    private static synchronized void incrementaAckEsperado() {
        if(ackEsperado == Long.MAX_VALUE)
            ackEsperado = 0;
        else
            ackEsperado++;
    }

    private static synchronized void setNumeroSequencia(long numero) {

        EnviaDados.numeroSequencia = numero;
    }

    private static synchronized void setAckEsperado(long ack) {
        EnviaDados.ackEsperado = ack;
    }

    private static synchronized void tresAcksDuplicados(long numACK) throws InterruptedException {
        System.out.println("Num ack que chegou: " + numACK);
        System.out.println("ackesperado: " + ackEsperado);
        if (numACK != ackEsperado && numACK != -1) {
            numeroAcksDuplicados ++;
            System.out.println(colors.MAGENTA + "ACK " + numACK + " DUPLICADO" + colors.RESET);

            if(numeroAcksDuplicados == 2){
                System.out.println(colors.RED + "REENVIO DE PACOTE POR TRÊS ACKS [" + numACK+ "] DUPLICADOS" + colors.RESET);

                setAckEsperado(numACK+1);

                precisaRetransmitir = true;
                primeiroPacoteARetransmitir = numACK+1;
                numeroAcksDuplicados = 0;
            }
        }
    }

    public String getFuncao() {return funcao;}


    private static void enviaPct(long[] dados, boolean ehRetransmissao) {

        //converte int[] para byte[]
        ByteBuffer byteBuffer = ByteBuffer.allocate(dados.length * 8);
        LongBuffer intBuffer = byteBuffer.asLongBuffer();
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
                    System.out.println(colors.RED + "Pacote " + primeiroPacoteARetransmitir + " perdido e Pacotes " + (primeiroPacoteARetransmitir+1)+ " ao " + numeroSequencia +" descartados." + colors.RESET);
                    for(long i = primeiroPacoteARetransmitir; i <= numeroSequencia; i ++){

                        if(bufferPacotes.get(i) != null) {
                            System.out.println(colors.RED + "RETRANSMITINDO: " + i + colors.RESET);

                            byte[] reenviar = bufferPacotes.get(i);

                            System.out.println("numseq: " + i + "---- dados[0]");

                            DatagramPacket packet = new DatagramPacket(reenviar, reenviar.length, address, portaDestino);
                            System.out.println(colors.GREEN + "Pacote " + i + " enviado." + colors.RESET);
                            datagramSocket.send(packet);
                        }
                    }
                    setAckEsperado(primeiroPacoteARetransmitir);
                    setNumeroSequencia(primeiroPacoteARetransmitir);
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
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void run() {
        switch (this.getFuncao()) {
            case "envia":

                long[] dados = new long[351]; // estrutura: [numeroSequencia, dados....]
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

                    DatagramSocket serverSocket = new DatagramSocket(portaLocalRecebimento);
                    byte[] receiveData = new byte[8];
                    long numACK = 0;

                    while (numACK != -1) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        var b = ByteBuffer.wrap(receivePacket.getData());
                        numACK = b.getLong();

                        tresAcksDuplicados(numACK);

                        if(numACK == -1)
                            System.out.println(colors.YELLOW + "ACK " + (numeroSequencia-1) + " recebido." + colors.RESET);
                        else if(numACK == ackEsperado)
                            System.out.println(colors.YELLOW + "ACK " + numACK + " recebido." + colors.RESET);

                        if(numACK == -1 && numACK != ackEsperado){
                            serverSocket.close();
                            break;
                        }

                        incrementaAckEsperado();
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
