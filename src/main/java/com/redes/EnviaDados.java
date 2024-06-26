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
    private static boolean precisaRetransmitir = false;
    private static long primeiroPacoteARetransmitir = 0;


    private static long numeroSequencia = 0;
    private static long ackEsperado = 0;
    private static int numeroAcksDuplicados = 0;
    private static long ultimoAckValido = -1;
    private static int semaforoNoMomentoDaPerda = 3;



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
        ackEsperado = ack;
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
                semaforoNoMomentoDaPerda = sem.availablePermits();
                System.out.println("sem na perda: " + semaforoNoMomentoDaPerda);
                primeiroPacoteARetransmitir = numACK+1;
                numeroAcksDuplicados = 0;
                precisaRetransmitir = true;
            }
        }
    }

    public String getFuncao() {return funcao;}


    private static void enviaPct(long[] dados, boolean ehRetransmissao) {

        //converte int[] para byte[]
        ByteBuffer byteBuffer = ByteBuffer.allocate(dados.length * 8);
        LongBuffer longBuffer = byteBuffer.asLongBuffer();
        longBuffer.put(dados);

        byte[] buffer = byteBuffer.array();
        bufferPacotes.put(dados[0], buffer.clone());

        try {

            System.out.println("Semaforo: " + sem.availablePermits());
            System.out.println("Semaforo: " + sem.availablePermits());

            InetAddress address = InetAddress.getByName("localhost");

                if(precisaRetransmitir){
                    System.out.println(colors.RED + "Pacote " + primeiroPacoteARetransmitir + " perdido e Pacotes " + (primeiroPacoteARetransmitir+1)+ " ao " + numeroSequencia +" descartados." + colors.RESET);

                    for(long i = primeiroPacoteARetransmitir; i <= numeroSequencia; i ++){
                        try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                            if (bufferPacotes.get(i) != null) {
                                System.out.println(colors.RED + "RETRANSMITINDO: " + i + colors.RESET);
                                System.out.println("Semaforozzzz: " + sem.availablePermits());

                                byte[] reenviar = bufferPacotes.get(i);

                                System.out.println("numseq: " + i + "---- dados[0]");

                                DatagramPacket packet = new DatagramPacket(reenviar, reenviar.length, address, portaDestino);
                                System.out.println(colors.GREEN + "Pacote " + i + " enviado." + colors.RESET);
                                datagramSocket.send(packet);
                                sem.acquire();
                            }
                        }
                        catch (InterruptedException e){
                            Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, e);
                            Thread.sleep(2000);
                        }
                    }
                    precisaRetransmitir = false;
                }
                else {
                    try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
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

                long[] dados = new long[351]; // [seq, dados...]
                int cont = 1;

                try (FileInputStream fileInput = new FileInputStream("entrada");) {
                    int lido;
                    while ((lido = fileInput.read()) != -1) {
                        dados[cont] = lido;
                        cont++;
                        if (cont == 351) {

                            dados[0] = numeroSequencia;
                            enviaPct(dados, false);
                            cont = 1; //reseta
                        }
                    }

                    for (int i = cont; i < 351; i++) //último pacote n terá o tamanho exato
                        dados[i] = -1;

                    dados[0] = numeroSequencia;
                    enviaPct(dados, false);
                    System.out.println("temrinou programa");
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

                        if(numACK == ackEsperado && numACK != ultimoAckValido) {
                            incrementaAckEsperado();
                            ultimoAckValido = numACK;
                            bufferPacotes.remove(numACK);
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
