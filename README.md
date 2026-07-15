<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 03 17 (2)" src="https://github.com/user-attachments/assets/0d59b367-474a-4a51-90e1-c4269c2aba23" /><img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 04 53 (2)" src="https://github.com/user-attachments/assets/8d96ea65-39a2-47af-9d85-e2863b72de89" /># Finance Manager

A comprehensive, high-performance Android application designed to help users take full control of their personal finances. This app combines traditional expense tracking with modern AI-powered features like OCR receipt scanning and automated recurring income management.

## 🚀 Key Features & Visual Tour

### 1. Smart Dashboard & Financial Overview
Get a high-level summary of your financial health at a glance. The dashboard aggregates data from all accounts to show total balance, recent transactions, and budget progress.
*   **Tech:** Custom SQL aggregation queries, efficient data binding, and real-time UI updates.

<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 04 26 (2)" src="https://github.com/user-attachments/assets/a2692655-6f06-4fd7-8326-901d5ca5fa4c" />

*Figure 1: Main Dashboard showing real-time financial summaries.*

### 2. AI-Powered OCR Receipt Scanning
Stop manual entry for physical purchases. Simply snap a photo of your receipt, and the app will automatically extract the amount, date, vendor, and currency.
*   **Tech:** Google ML Kit (Text Recognition), Android CameraX API for high-quality image capture, and custom parsing logic for receipt data.
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 05 26 (2)" src="https://github.com/user-attachments/assets/02f9071a-c81f-4cbf-bc1a-a2b167d73a40" />

*Figure 2: Automated data extraction from physical receipts using Machine Learning.*

### 3. Advanced Budgeting System
Set strict or flexible financial goals across different timeframes—Daily, Weekly, Monthly, or Yearly. The app provides a "Global Budget" fallback to ensure you never overspend, even without specific plans.
*   **Tech:** Complex SQLite logic with date-range overlaps and automated budget distribution algorithms.
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 04 45 (2)" src="https://github.com/user-attachments/assets/ac9e571b-b098-4475-8819-5042cae9e437" />

*Figure 3: Setting and tracking multi-period financial goals.*

### 4. Interactive Financial Analytics
Visualize your spending patterns through professional-grade charts. Analyze expenses by category over time to identify where your money goes.
*   **Tech:** MPAndroidChart integration with custom styling and dynamic data filtering.
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 04 53 (2)" src="https://github.com/user-attachments/assets/b3ea1253-5adc-4e8f-ab72-d9fa733d3afd" />
*Figure 4: Detailed spending analytics and category distribution charts.*

### 5. Automated Recurring Incomes
Automate your regular income streams. Whether it's a monthly salary or weekly freelance pay, the app schedules and reconciles these entries automatically.
*   **Tech:** Custom background reconciliation logic that materializes future occurrences based on user-defined frequency rules.
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 07 24 (2)" src="https://github.com/user-attachments/assets/994d8c6e-5376-4682-bf16-b2b831744cfb" />

*Figure 5: Managing automated recurring income rules.*

### 6. Transaction Management & Smart Categorization
Detailed logging for every transaction with support for notes, custom categories, and status tracking (e.g., voiding or reconciliation).
*   **Tech:** Robust SQLite database with foreign key constraints, indexing for performance, and ACID-compliant transactions.
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 08 40 (2)" src="https://github.com/user-attachments/assets/27f4f5a3-adc3-4552-8528-7a9ad71a3780" />

*Figure 6: Comprehensive list and management of historical transactions.*

### 7. Secure User Profiles & Authentication
Full user lifecycle management including secure registration, login with hashed credentials, and customizable profile settings.
*   **Tech:** Session management, password hashing (BCrypt/PBKDF2 style), and local storage of user preferences.
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 09 24 (2)" src="https://github.com/user-attachments/assets/ec75d503-cb2a-4f0a-8791-11731173fde7" />
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 03 45 (2)" src="https://github.com/user-attachments/assets/c72e6d62-24b3-4367-8fac-c75c8786c56b" />
<img width="1512" height="982" alt="Screenshot 2026-07-15 at 13 03 17 (2)" src="https://github.com/user-attachments/assets/d2187455-1e6b-4230-b175-fda135f7e7f0" />


*Figure 7: Secure user authentication and profile personalization.*



*Figure 6: Secure user authentication and profile personalization.*

## 🛠 Tech Stack & Libraries

*   **Language:** Java
*   **UI Framework:** XML with Material Design Components
*   **Database:** SQLite (Custom `SQLiteOpenHelper` implementation)
*   **Architecture:** Layered Architecture (UI, Database, Utilities)
*   **AI & Vision:** 
    *   Google ML Kit (Text Recognition)
    *   Android CameraX
*   **Charts:** MPAndroidChart
*   **Utilities:** 
    *   Java Time API (via Desugaring)
    *   Google Guava
    *   Custom Security/Hashing Utilities

## 🏗 Architecture & Project Structure

The project follows a clean, modular structure organized by concerns:

*   **`com.example.finance.ui`**: View layer containing Activities for every functional module.
*   **`com.example.finance.db`**: Centralized data access layer with complex schema management.
*   **`com.example.finance.util`**: Reusable utility classes for dates, security, and sessions.

## 🚦 Getting Started

### Prerequisites
*   Android Studio Ladybug (or newer)
*   Android SDK 35 (Compile SDK)
*   Minimum Android Version: API 21 (Lollipop)

### Installation
1.  **Clone the repository:** `git clone https://github.com/your-username/finance-android.git`
2.  **Open in Android Studio:** Select the project folder.
3.  **Sync Gradle:** Let Android Studio download dependencies.
4.  **Run:** Build and run on a device or emulator.

---

*Developed with a focus on clean code, performance, and user experience.*
