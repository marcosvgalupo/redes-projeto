package com.redes;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnviaDados extends Thread {

    private static final int portaLocalEnvio = 2000;
    private static final int portaDestino = 2001;
    private final int portaLocalRecebimento = 2003;
    private String ipMaquina;

    private static final HashMap<Long, byte[]> bufferPacotes = new HashMap<>();
    private static Timeout timeout;

    private static final TerminalColors colors = null; // assuming TerminalColors is defined elsewhere

    static Semaphore sem;
    private final String funcao;
    private static boolean precisaRetransmitir = false;
    private static long primeiroPacoteARetransmitir = 0;

    private static long numeroSequencia = 0;
    private static long ackEsperado = 0;
    private static int numeroAcksDuplicados = 0;
    private static long ultimoAckValido = -1;
    private static int semaforoNoMomentoDaPerda = 3;
    private static HashMap<Long, Boolean> pacotesJaConfirmados = new HashMap<>();
    private static boolean envioCompleto = false; // sinalizador para indicar o término do envio
    private static boolean leituraCompleta = false; // sinalizador para indicar o término da leitura do arquivo

    public EnviaDados(Semaphore sem, String funcao, String ipMaquina) {
        super(funcao);
        EnviaDados.sem = sem; // corrigido para usar o campo estático
        this.funcao = funcao;
        this.ipMaquina = ipMaquina;
    }

    private static synchronized void incrementaNumeroSequencia() {
        if (numeroSequencia == Long.MAX_VALUE) {
            numeroSequencia = 0;
        } else {
            numeroSequencia++;
        }
    }

    private static synchronized void incrementaAckEsperado() {
        if (ackEsperado == Long.MAX_VALUE) {
            ackEsperado = 0;
        } else {
            ackEsperado++;
        }
    }

    private static synchronized void setNumeroSequencia(long numero) {
        EnviaDados.numeroSequencia = numero;
    }

    private static synchronized void setAckEsperado(long ack) {
        ackEsperado = ack;
    }

    private static synchronized void tresAcksDuplicados(long numACK) throws InterruptedException {
        if (numACK == ackEsperado) {
            numeroAcksDuplicados = 0; // resetar contagem ao receber o ACK esperado
        } else {
            numeroAcksDuplicados++;
            System.out.println(colors.MAGENTA + "ACK DUPLICADO" + colors.RESET);

            if (numeroAcksDuplicados >= 2) {
                setAckEsperado(numACK + 1);
                primeiroPacoteARetransmitir = numACK + 1;
                numeroAcksDuplicados = 0;
                precisaRetransmitir = true;
            }
        }
    }

    public String getFuncao() {
        return funcao;
    }

    private static void enviaPct(long[] dados, boolean ehRetransmissao) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(dados.length * 8);
        LongBuffer longBuffer = byteBuffer.asLongBuffer();
        longBuffer.put(dados);

        byte[] buffer = byteBuffer.array();
        bufferPacotes.put(dados[0], buffer.clone());
        pacotesJaConfirmados.putIfAbsent(dados[0], false); // inicializa como não confirmado

        try {
            InetAddress address = InetAddress.getByName("localhost");

            if (precisaRetransmitir) {
                System.out.println(colors.RED + "Pacote " + primeiroPacoteARetransmitir + " perdido e Pacotes " + (primeiroPacoteARetransmitir + 1) + " ao " + numeroSequencia + " descartados." + colors.RESET);

                for (long i = primeiroPacoteARetransmitir; i <= numeroSequencia; i++) {
                    try (DatagramSocket datagramSocket = new DatagramSocket(portaLocalEnvio)) {
                        if (bufferPacotes.get(i) != null) {
                            System.out.println(colors.RED + "RETRANSMITINDO: " + i + colors.RESET);
                            byte[] reenviar = bufferPacotes.get(i);
                            DatagramPacket packet = new DatagramPacket(reenviar, reenviar.length, address, portaDestino);
                            System.out.println(colors.GREEN + "[RETRANSMISSÃO]Pacote " + i + " enviado." + colors.RESET);
                            datagramSocket.send(packet);
                            sem.acquire();
                        }
                    } catch (InterruptedException e) {
                        Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, e);
                    }
                }
                precisaRetransmitir = false;
            } else {
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
                long[] dados = new long[351];
                int cont = 1;

                try (FileInputStream fileInput = new FileInputStream("entrada")) {
                    int lido;
                    while ((lido = fileInput.read()) != -1) {
                        dados[cont] = lido;
                        cont++;
                        if (cont == 351) {
                            dados[0] = numeroSequencia;
                            enviaPct(dados, false);
                            cont = 1;
                        }
                    }

                    for (int i = cont; i < 351; i++)
                        dados[i] = -1;

                    dados[0] = numeroSequencia;
                    enviaPct(dados, false);
                } catch (IOException e) {
                    System.out.println("Error message: " + e.getMessage());
                }

                leituraCompleta = true; // sinaliza que a leitura do arquivo foi completada

                // Verifica se há pacotes não confirmados e continua retransmitindo até todos serem confirmados
                while (!pacotesJaConfirmados.values().stream().allMatch(Boolean::booleanValue)) {
                    if (precisaRetransmitir) {
                        for (long i = primeiroPacoteARetransmitir; i <= numeroSequencia; i++) {
                            if (bufferPacotes.get(i) != null) {
                                enviaPct(new long[]{i}, true);
                            }
                        }
                    }
                    try {
                        Thread.sleep(100); // aguarda um pouco antes de verificar novamente
                    } catch (InterruptedException e) {
                        Logger.getLogger(EnviaDados.class.getName()).log(Level.SEVERE, null, e);
                    }
                }
                envioCompleto = true; // sinaliza que o envio foi completado
                break;

            case "ack":
                try (DatagramSocket serverSocket = new DatagramSocket(portaLocalRecebimento)) {
                    byte[] receiveData = new byte[8];
                    long numACK = 0;

                    while (!envioCompleto || !pacotesJaConfirmados.values().stream().allMatch(Boolean::booleanValue)) {
                        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                        serverSocket.receive(receivePacket);
                        var b = ByteBuffer.wrap(receivePacket.getData());
                        numACK = b.getLong();
                        System.out.println("numero de sequencia atual: "  + numACK);
                        System.out.println("numero de sequencia esperado: " + ackEsperado);

                        tresAcksDuplicados(numACK);

                        if (numACK == -1)
                            System.out.println(colors.YELLOW + "ACK " + numACK + " recebido." + colors.RESET);
                        else if (numACK == ackEsperado)
                            System.out.println(colors.YELLOW + "ACK " + numACK + " recebido." + colors.RESET);

                        if (numACK == -1 && numACK != ackEsperado) {
                            setNumeroSequencia(-1);
                            envioCompleto = true;
                            pacotesJaConfirmados.put(ultimoAckValido+1, true);
                            break;
                        }

                        if (numACK == ackEsperado && numACK != ultimoAckValido) {
                            incrementaAckEsperado();
                            ultimoAckValido = numACK;
                            bufferPacotes.remove(numACK);
                            pacotesJaConfirmados.put(numACK, true);
                        }

                        sem.release();
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("Excecao: " + e.getMessage());
                }
                break;

            case "timeout":
                // Implementação do timeout aqui
                break;

            default:
                break;
        }
    }
}
