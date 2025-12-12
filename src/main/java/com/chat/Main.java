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
                System.out.println("4. Nutraukti kontraktą");
                System.out.println("5. Peržiūrėti žaidėjus konkrečioje komandoje");
                System.out.println("6. Pridėti žaidėjui kontraktą");
                System.out.println("7. Peržiūrėti Turnyrinę Lentelę");
                System.out.println("0. Išeiti");

                System.out.print("Pasirinkimas: ");
                int pasirinkimas = scanner.nextInt();
                scanner.nextLine();

                switch (pasirinkimas) {
                    case 1: registruotiZaideja(conn, scanner); break;
                    case 2: perkeltiZaideja(conn, scanner); break;
                    case 3: ieskotiZaidejo(conn, scanner); break;
                    case 4: nutrauktiKontrakta(conn, scanner); break;
                    case 5: perziuretiVisusKomandosZaidejus(conn, scanner); break;
                    case 6: pridetiZaidejuiKontrakta(conn, scanner); break;
                    case 7: atvaizduotiTurnyrineLentele(conn); break;
                    case 0: return;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void registruotiZaideja(Connection conn, Scanner scanner) throws SQLException {

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

            conn.commit();
            System.out.println("Žaidėjas sėkmingai perkeltas (Transakcija sėkminga).");
        } catch (SQLException e) {
            conn.rollback();
            System.out.println("Klaida! Transakcija atšaukta: " + e.getMessage());
        } finally {
            conn.setAutoCommit(true);
        }
    }

    // -- Paieška per 2 susijusias lenteles ---
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

    private static void nutrauktiKontrakta(Connection conn, Scanner scanner) throws SQLException {
        System.out.println("\n--- Kontrakto ir narystės nutraukimas (Istorijos išsaugojimas) ---");

        // Parodome visus ŠIUO METU aktyvius žaidėjus ir jų komandas
        String sqlList = "SELECT z.zaidejo_id, z.vardas, z.pavarde, k.pavadinimas as komanda, k.komandos_id " +
                "FROM Zaidejai z " +
                "JOIN Zaidejo_Komanda zk ON z.zaidejo_id = zk.zaidejo_id " +
                "JOIN Komandos k ON zk.komandos_id = k.komandos_id " +
                "WHERE zk.pabaiga IS NULL";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sqlList)) {
            System.out.println("Aktyvūs žaidėjai komandose:");
            while (rs.next()) {
                System.out.printf("Žaidėjo ID: %d | %s %s (%s)\n",
                        rs.getInt("zaidejo_id"), rs.getString("vardas"),
                        rs.getString("pavarde"), rs.getString("komanda"));
            }
        }

        System.out.print("\nĮveskite žaidėjo ID, kurio kontraktą norite nutraukti: ");
        int zId = scanner.nextInt();
        System.out.print("Įveskite nutraukimo datą (YYYY-MM-DD): ");
        String data = scanner.next();

        try {
            conn.setAutoCommit(false);

            // Atnaujiname Zaidejo_Komanda (nustatome pabaigos datą)
            String sqlUpdateNaryste = "UPDATE Zaidejo_Komanda SET pabaiga = ? " +
                    "WHERE zaidejo_id = ? AND pabaiga IS NULL";
            try (PreparedStatement pstmt1 = conn.prepareStatement(sqlUpdateNaryste)) {
                pstmt1.setDate(1, java.sql.Date.valueOf(data));
                pstmt1.setInt(2, zId);
                int rows1 = pstmt1.executeUpdate();
                if (rows1 == 0) throw new SQLException("Žaidėjas neturi aktyvios narystės komandoje.");
            }

            // Atnaujiname Kontraktai lentelę (nustatome kontrakto pabaigą)
            String sqlUpdateKontraktas = "UPDATE Kontraktai SET kontrakto_pabaiga = ? " +
                    "WHERE zaidejo_id = ? AND kontrakto_pabaiga > ?";
            try (PreparedStatement pstmt2 = conn.prepareStatement(sqlUpdateKontraktas)) {
                pstmt2.setDate(1, java.sql.Date.valueOf(data));
                pstmt2.setInt(2, zId);
                pstmt2.setDate(3, java.sql.Date.valueOf(data));
                pstmt2.executeUpdate();
            }

            conn.commit();
            System.out.println("Sėkmingai užfiksuotas kontrakto nutraukimas. Istoriniai duomenys išsaugoti.");

        } catch (SQLException e) {
            conn.rollback();
            System.err.println("Klaida nutraukiant kontraktą: " + e.getMessage());
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void perziuretiVisusKomandosZaidejus(Connection conn, Scanner scanner) throws SQLException {
        System.out.println("Esamos komandos:");
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT komandos_id, pavadinimas, miestas FROM Komandos");
        while (rs.next()) {
            System.out.printf("ID: %d | %s (%s)\n", rs.getInt("komandos_id"), rs.getString("pavadinimas"), rs.getString("miestas"));
        }

        System.out.print("Įveskite pasirinktos komandos ID: ");
        int kId = scanner.nextInt();
        scanner.nextLine();

        String sql = "SELECT z.vardas, z.pavarde, zk.marskineliu_nr, zk.pozicija " +
                "FROM Zaidejai z " +
                "JOIN Zaidejo_Komanda zk ON z.zaidejo_id = zk.zaidejo_id " +
                "WHERE zk.komandos_id = ? AND (zk.pabaiga IS NULL OR zk.pabaiga > CURRENT_DATE) " +
                "ORDER BY zk.marskineliu_nr";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, kId);

            try (ResultSet rss = pstmt.executeQuery()) {
                System.out.println("\nŽaidėjų sąrašas:");
                System.out.println("--------------------------------------------------");
                boolean rasta = false;
                while (rss.next()) {
                    rasta = true;
                    System.out.printf("#%d | %-10s %-15s | Pozicija: %s\n",
                            rss.getInt("marskineliu_nr"),
                            rss.getString("vardas"),
                            rss.getString("pavarde"),
                            rss.getString("pozicija"));
                }

                if (!rasta) {
                    System.out.println("Ši komanda šiuo metu neturi aktyvių žaidėjų.");
                }
                System.out.println("--------------------------------------------------");
            }
        }
    }

    private static void pridetiZaidejuiKontrakta(Connection conn, Scanner scanner) throws SQLException {
        System.out.println("\n--- Naujos sutarties pasirašymas ---");

        // 1. Parodome žaidėjus, kad vartotojas žinotų ID (Reikalavimas: išvesti sąrašą prieš įvedant ID)
        System.out.println("Laisvieji žaidėjai (arba visi žaidėjai):");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT zaidejo_id, vardas, pavarde FROM Zaidejai")) {
            while (rs.next()) {
                System.out.printf("ID: %d | %s %s\n", rs.getInt("zaidejo_id"), rs.getString("vardas"), rs.getString("pavarde"));
            }
        }

        System.out.print("Pasirinkite žaidėjo ID: ");
        int zId = scanner.nextInt();

        // 2. Parodome komandas
        System.out.println("Esamos komandos:");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT komandos_id, pavadinimas FROM Komandos")) {
            while (rs.next()) {
                System.out.printf("ID: %d | %s\n", rs.getInt("komandos_id"), rs.getString("pavadinimas"));
            }
        }
        System.out.print("Pasirinkite komandos ID: ");
        int kId = scanner.nextInt();

        // 3. Įvedame kontrakto duomenis
        System.out.print("Kontrakto vertė (eurais): ");
        double verte = scanner.nextDouble();
        System.out.print("Kontrakto pradžia (YYYY-MM-DD): ");
        String pradzia = scanner.next();
        System.out.print("Kontrakto pabaiga (YYYY-MM-DD): ");
        String pabaiga = scanner.next();
        System.out.print("Kontrakto tipas (standartinis, naujoko, dvisalis): ");
        String tipas = scanner.next();
        System.out.print("Marškinėlių nr: ");
        String nr = scanner.next();
        System.out.print("Pozicija (PG, SG, SF, PF, C): ");
        String poz = scanner.next().toUpperCase();

        try {
            conn.setAutoCommit(false);

            // A. Įterpiame į kontraktu lentelę
            String sqlKontraktas = "INSERT INTO Kontraktai (zaidejo_id, komandos_id, verte, kontrakto_pradzia, kontrakto_pabaiga, kontrakto_tipas) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmtK = conn.prepareStatement(sqlKontraktas)) {
                pstmtK.setInt(1, zId);
                pstmtK.setInt(2, kId);
                pstmtK.setDouble(3, verte);
                pstmtK.setDate(4, java.sql.Date.valueOf(pradzia));
                pstmtK.setDate(5, java.sql.Date.valueOf(pabaiga));
                pstmtK.setString(6, tipas);
                pstmtK.executeUpdate();
            }

            // B. Įterpiame į Zaidejo_Komanda lentelę (kad jis taptų komandos nariu)
            String sqlNaryste = "INSERT INTO Zaidejo_Komanda (zaidejo_id, komandos_id, pradzia, pozicija, marskineliu_nr) " +
                    "VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmtN = conn.prepareStatement(sqlNaryste)) {
                pstmtN.setInt(1, zId);
                pstmtN.setInt(2, kId);
                pstmtN.setDate(3, java.sql.Date.valueOf(pradzia));
                pstmtN.setObject(4, poz, java.sql.Types.OTHER);
                pstmtN.setInt(5, Integer.parseInt(nr));
                pstmtN.executeUpdate();
            }

            conn.commit(); // Jei abu veiksmai pavyko, patvirtiname
            System.out.println("Sutartis sėkmingai pasirašyta ir žaidėjas priskirtas komandai!");

        } catch (SQLException e) {
            conn.rollback(); // Jei bet kuri dalis nepavyko, atšaukiame viską
            System.err.println("Klaida registruojant sutartį. Transakcija atšaukta: " + e.getMessage());
        } finally {
            conn.setAutoCommit(true); // Grąžiname į pradinę būseną
        }
    }


    private static void atvaizduotiTurnyrineLentele(Connection conn) throws SQLException {
        System.out.println("\n--- Turnyrinė lentelė ---");

        try (Statement stmt = conn.createStatement()) {

            // 1. Atnaujiname materializuotą vaizdą
            System.out.println("Atnaujinama turnyrinė lentelė...");
            // Būtina naudoti dvigubas kabutes aplink vaizdo pavadinimą
            stmt.executeUpdate("REFRESH MATERIALIZED VIEW \"komandu_turnyrine_lentele\"");
            System.out.println("Lentelė atnaujinta sėkmingai.");

            // 2. Skaitome duomenis iš atnaujinto vaizdo
            // PATAISYTA: "tasku_skirtumai" pakeistas į "taskų_skirtumai" su kabutėmis
            String sql = "SELECT Sezonas, Komanda, Laimejimai, Pralaimejimai, \"taskų_skirtumai\" " +
                    "FROM \"komandu_turnyrine_lentele\" " +
                    "ORDER BY Sezonas, Laimejimai DESC, \"taskų_skirtumai\" DESC";

            try (ResultSet rs = stmt.executeQuery(sql)) {

                // 3. Spausdiname rezultatus gražiu formatu
                System.out.println("\n----------------------------------------------------------------------------------");
                System.out.printf("| %-10s | %-20s | %-12s | %-12s | %-12s |\n",
                        "Sezonas", "Komanda", "Laimėjimai", "Pralaimėjimai", "Taškų skirt.");
                System.out.println("----------------------------------------------------------------------------------");

                String currentSeason = "";
                boolean rasta = false;

                while (rs.next()) {
                    rasta = true;
                    String sezonas = rs.getString("Sezonas");
                    String komanda = rs.getString("Komanda");
                    int laim = rs.getInt("Laimejimai");
                    int pral = rs.getInt("Pralaimejimai");

                    // PATAISYTA: Naudojama teisinga stulpelio etiketė su "ų"
                    int skirtumas = rs.getInt("taskų_skirtumai");

                    // Atribojimas tarp skirtingų sezonų
                    if (!sezonas.equals(currentSeason)) {
                        if (currentSeason.length() > 0) {
                            System.out.println("|------------|----------------------|--------------|--------------|--------------|");
                        }
                        currentSeason = sezonas;
                    }

                    // Naudojame printf formatavimą (naudojame %+12d, kad būtų + arba - prieš skirtumą)
                    System.out.printf("| %-10s | %-20s | %-12d | %-12d | %+12d |\n",
                            sezonas, komanda, laim, pral, skirtumas);
                }

                if (!rasta) {
                    System.out.println("| Nėra duomenų turnyrinėje lentelėje.                                            |");
                }
                System.out.println("----------------------------------------------------------------------------------");
            }
        } catch (SQLException e) {
            System.err.println("Klaida atnaujinant ar skaitant turnyrinę lentelę. Ar tikrai sukurtas MV \"komandu_turnyrine_lentele\"? " + e.getMessage());
            throw e;
        }
    }
}