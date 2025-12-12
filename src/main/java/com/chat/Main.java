package com.chat;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.*;
import java.util.Scanner;

public class Main {
    private static final Dotenv dotenv = Dotenv.load();

    private static final String URL = dotenv.get("DB_URL");
    private static final String USER = dotenv.get("DB_USER");
    private static final String PASS = dotenv.get("DB_PASS");

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS)) {
            System.out.println("Prisijungta prie DB!");
            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.println("\n--- Krepšinio Valdymo Sistema ---");
                System.out.println("1. Registruoti naują žaidėją komandai (ID pasirinkimas su sąrašu)");
                System.out.println("2. Perkelti žaidėją į kitą komandą (Transakcija)");
                System.out.println("3. Ieškoti žaidėjo pagal pavardę");
                System.out.println("4. Pašalinti pasibaigusį kontraktą");
                System.out.println("0. Išeiti");

                int pasirinkimas = scanner.nextInt();
                scanner.nextLine();

                switch (pasirinkimas) {
                    case 1: registruotiZaideja(conn, scanner); break;
                    case 2: perkeltiZaideja(conn, scanner); break;
                    case 3: ieskotiZaidejo(conn, scanner); break;
                    case 4: salintiKontrakta(conn, scanner); break;
                    case 0: return;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ID pasirinkimas išvedant sąrašą ---
    private static void registruotiZaideja(Connection conn, Scanner scanner) throws SQLException {
        // Pirmiausia parodome komandas
        System.out.println("Esamos komandos:");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT komandos_id, pavadinimas, miestas FROM Komandos");
        while (rs.next()) {
            System.out.printf("ID: %d | %s (%s)\n", rs.getInt("komandos_id"), rs.getString("pavadinimas"), rs.getString("miestas"));
        }

        System.out.print("Įveskite pasirinktos komandos ID: ");
        int kId = scanner.nextInt();
        scanner.nextLine();
        System.out.print("Žaidėjo vardas: ");
        String vardas = scanner.next();
        System.out.print("Žaidėjo pavardė: ");
        String pavarde = scanner.next();
        System.out.print("Žaidėjo gimimo data (formatas YYYY-MM-DD): ");
        String gim_data = scanner.next();
        System.out.print("Žaidėjo ūgis (cm): ");
        String ugis = scanner.next();
        System.out.print("Žaidėjo svoris: ");
        String svoris = scanner.next();

        // PreparedStatement (Apsauga nuo injekcijų)
        String sql = "INSERT INTO Zaidejai (vardas, pavarde, gimimo_data, ugis, svoris) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, vardas);
            pstmt.setString(2, pavarde);
            pstmt.setDate(3, Date.valueOf(gim_data));
            pstmt.setInt(4, Integer.parseInt(ugis));
            pstmt.setInt(5, Integer.parseInt(svoris));
            pstmt.executeUpdate();
            System.out.println("Žaidėjas sėkmingai užregistruotas.");
        }
    }

    private static void perkeltiZaideja(Connection conn, Scanner scanner) throws SQLException {
        System.out.print("Įveskite žaidėjo ID: ");
        int zId = scanner.nextInt();
        System.out.print("Įveskite NAUJOS komandos ID: ");
        int kId = scanner.nextInt();

        try {
            conn.setAutoCommit(false); // Transakcijos pradžia

            String sql1 = "UPDATE Zaidejo_Komanda SET pabaiga = CURRENT_DATE WHERE zaidejo_id = ? AND pabaiga IS NULL";
            try (PreparedStatement p1 = conn.prepareStatement(sql1)) {
                p1.setInt(1, zId);
                p1.executeUpdate();
            }

            // 2 sakinys: Įterpiame naują įrašą
            String sql2 = "INSERT INTO Zaidejo_Komanda (zaidejo_id, komandos_id, pradzia, pozicija) VALUES (?, ?, CURRENT_DATE, 'PG')";
            try (PreparedStatement p2 = conn.prepareStatement(sql2)) {
                p2.setInt(1, zId);
                p2.setInt(2, kId);
                p2.executeUpdate();
            }

            conn.commit(); // Patvirtiname transakciją
            System.out.println("Žaidėjas sėkmingai perkeltas (Transakcija sėkminga).");
        } catch (SQLException e) {
            conn.rollback(); // Jei klaida - atšaukiame viską
            System.out.println("Klaida! Transakcija atšaukta: " + e.getMessage());
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // --- REIKALAVIMAS: Paieška per 2 susijusias lenteles ---
    private static void ieskotiZaidejo(Connection conn, Scanner scanner) throws SQLException {
        System.out.print("Įveskite ieškomą pavardę: ");
        String pav = scanner.next();

        String sql = "SELECT z.vardas, z.pavarde, k.pavadinimas as komanda " +
                "FROM Zaidejai z " +
                "LEFT JOIN Zaidejo_Komanda zk ON z.zaidejo_id = zk.zaidejo_id " +
                "LEFT JOIN Komandos k ON zk.komandos_id = k.komandos_id " +
                "WHERE z.pavarde LIKE ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, "%" + pav + "%");
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                System.out.println(rs.getString("vardas") + " " + rs.getString("pavarde") + " - Komanda: " + rs.getString("komanda"));
            }
        }
    }

    // --- REIKALAVIMAS: Duomenų trynimas ---
    private static void salintiKontrakta(Connection conn, Scanner scanner) throws SQLException {
        System.out.print("Įveskite kontrakto ID, kurį norite anuliuoti: ");
        int cId = scanner.nextInt();

        String sql = "DELETE FROM Kontraktai WHERE kontrakto_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, cId);
            int rows = pstmt.executeUpdate();
            if (rows > 0) System.out.println("Kontraktas anuliuotas.");
            else System.out.println("Kontraktas nerastas.");
        }
    }}