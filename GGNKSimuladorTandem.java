import java.util.*;

public class GGNKSimuladorTandem {

    // ---------------- Gerador Congruente Linear ----------------
    static class LCG {
        long a, c, m, seed, usados, limite;
        public LCG(long a, long c, long m, long seed, long limite) {
            this.a = a; this.c = c; this.m = m; this.seed = seed; this.limite = limite;
        }
        public double nextRandom() { seed = ((a * seed) + c) % m; usados++; return (double) seed / (double) m; }
        public boolean atingiuLimite() { return usados >= limite; }
    }

    enum Tipo { CHEGADA_FILA1, SAIDA_FILA1, CHEGADA_FILA2, SAIDA_FILA2 }

    static class Evento implements Comparable<Evento> {
        Tipo tipo; double tempo;
        Evento(Tipo t, double tempo) { this.tipo = t; this.tempo = tempo; }
        public int compareTo(Evento o) { return Double.compare(this.tempo, o.tempo); }
    }

    // ---------------- Classe Fila  ----------------
    static class Fila {
        int Server; int Capacity;
        double MinArrival, MaxArrival;       // só usado em F1
        double MinService, MaxService;
        int Customers = 0, Loss = 0;
        double[] Times;

        private int inService = 0;           // controle interno

        Fila(int server, int capacity,
             double minArrival, double maxArrival,
             double minService, double maxService) {
            this.Server = server; this.Capacity = capacity;
            this.MinArrival = minArrival; this.MaxArrival = maxArrival;
            this.MinService = minService; this.MaxService = maxService;
            this.Times = new double[capacity + 1];
        }

        double sampleArrival(LCG rng){ return MinArrival + (MaxArrival - MinArrival) * rng.nextRandom(); }
        double sampleService(LCG rng){ return MinService + (MaxService - MinService) * rng.nextRandom(); }

        boolean hasSpace()             { return Customers < Capacity; }
        boolean canStartMore()         { return inService < Server && inService < Customers; }
        void enter()                   { if (Customers < Capacity) Customers++; else Loss++; }
        void leave()                   { if (Customers > 0) { Customers--; if (inService > 0) inService--; } }
        void startOne()                { if (inService < Server && Customers > inService) inService++; }
    }

    // ---------------- Simulador Tandem ----------------
    static class SimuladorTandem {
        final int MAX_EVENTOS = 100_000;
        double clock = 0.0, tempoGlobal = 0.0;
        int eventosProcessados = 0;

        final PriorityQueue<Evento> agenda = new PriorityQueue<Evento>();
        final Fila f1, f2;
        final LCG rng;

        SimuladorTandem(Fila f1, Fila f2, LCG rng) { this.f1 = f1; this.f2 = f2; this.rng = rng; }

        void primeiroEvento() { agenda.add(new Evento(Tipo.CHEGADA_FILA1, 1.5)); }

        boolean podeAgendar() { return eventosProcessados < MAX_EVENTOS && !rng.atingiuLimite(); }

        void iniciarServicosEmLote(Fila f, Tipo saida) {
            while (f.canStartMore() && podeAgendar()) {
                f.startOne();
                double ts = f.sampleService(rng);
                agenda.add(new Evento(saida, clock + ts));
            }
        }

        void iniciaSimulacao() {
            primeiroEvento();
            while (!agenda.isEmpty() && eventosProcessados < MAX_EVENTOS) {
                Evento e = agenda.poll();

                // acumula tempos por estado
                double dt = e.tempo - clock; if (dt < 0) dt = 0;
                if (f1.Customers >= 0 && f1.Customers < f1.Times.length) f1.Times[f1.Customers] += dt;
                if (f2.Customers >= 0 && f2.Customers < f2.Times.length) f2.Times[f2.Customers] += dt;
                clock = e.tempo;

                switch (e.tipo) {
                    case CHEGADA_FILA1:
                        if (podeAgendar()) {
                            double prox = clock + f1.sampleArrival(rng); // chegadas externas só em F1
                            agenda.add(new Evento(Tipo.CHEGADA_FILA1, prox));
                        }
                        if (f1.hasSpace()) {
                            f1.enter();
                            iniciarServicosEmLote(f1, Tipo.SAIDA_FILA1);
                        } else f1.Loss++;
                        break;

                    case SAIDA_FILA1:
                        f1.leave();
                        // transferência 100% para F2
                        if (f2.hasSpace()) {
                            if (podeAgendar()) agenda.add(new Evento(Tipo.CHEGADA_FILA2, clock));
                        } else f2.Loss++;
                        iniciarServicosEmLote(f1, Tipo.SAIDA_FILA1);
                        break;

                    case CHEGADA_FILA2:
                        f2.enter();
                        iniciarServicosEmLote(f2, Tipo.SAIDA_FILA2);
                        break;

                    case SAIDA_FILA2:
                        f2.leave();
                        iniciarServicosEmLote(f2, Tipo.SAIDA_FILA2);
                        break;
                }

                eventosProcessados++;
                if (rng.atingiuLimite() || eventosProcessados >= MAX_EVENTOS) break;
            }
            tempoGlobal = clock;
        }

        void resultadoFinal() {
            double tot1 = 0.0; for (double t : f1.Times) tot1 += t;
            double tot2 = 0.0; for (double t : f2.Times) tot2 += t;

            System.out.println("=== RESULTADOS DA SIMULACAO ===");
            System.out.printf("Tempo global: %.4f%n", tempoGlobal);
            System.out.printf("Eventos processados: %d%n", eventosProcessados);
            System.out.printf("Aleatorios usados: %d%n%n", rng.usados);

            System.out.println("FILA 1 (G/G/2/3):");
            System.out.printf("Perdas: %d%n", f1.Loss);
            for (int i = 0; i < f1.Times.length; i++) {
                double p = (tot1 > 0) ? (f1.Times[i] / tot1) * 100.0 : 0.0;
                System.out.printf("  Estado %d: tempo = %.4f, prob = %.6f%%%n", i, f1.Times[i], p);
            }
            System.out.println();

            System.out.println("FILA 2 (G/G/1/5):");
            System.out.printf("Perdas: %d%n", f2.Loss);
            for (int i = 0; i < f2.Times.length; i++) {
                double p = (tot2 > 0) ? (f2.Times[i] / tot2) * 100.0 : 0.0;
                System.out.printf("  Estado %d: tempo = %.4f, prob = %.6f%%%n", i, f2.Times[i], p);
            }
        }
    }

    // ---------------- MAIN (M6) ----------------
    public static void main(String[] args) {
        long M = (long) Math.pow(2, 32);
        long a = 1664525, c = 1013904223, seed = 5;

        long aleatorios = 100000;

        // F1: G/G/2/3, chegadas U[1,4], serviço U[3,4]
        Fila fila1 = new Fila(2, 3, 1.0, 4.0, 3.0, 4.0);
        // F2: G/G/1/5, serviço U[2,3] (sem chegadas externas)
        Fila fila2 = new Fila(1, 5, 0.0, 0.0, 2.0, 3.0);

        LCG rng = new LCG(a, c, M, seed, aleatorios);

        System.out.println("---- Simulação de Filas em Tandem ----");
        SimuladorTandem sim = new SimuladorTandem(fila1, fila2, rng);
        sim.iniciaSimulacao();
        sim.resultadoFinal();
    }
}
