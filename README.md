# Finance Manager

A comprehensive, high-performance Android application designed to help users take full control of their personal finances. This app combines traditional expense tracking with modern AI-powered features like OCR receipt scanning and automated recurring income management.

## 🚀 Key Features & Visual Tour

### 1. Smart Dashboard & Financial Overview
Get a high-level summary of your financial health at a glance. The dashboard aggregates data from all accounts to show total balance, recent transactions, and budget progress.
*   **Tech:** Custom SQL aggregation queries, efficient data binding, and real-time UI updates.

<img width="1344" height="2992" alt="Screenshot_20260715_124618" src="https://github.com/user-attachments/assets/96b09da5-b895-459a-a747-2fdb10a5e625" />
*Figure 1: Main Dashboard showing real-time financial summaries.*

### 2. AI-Powered OCR Receipt Scanning
Stop manual entry for physical purchases. Simply snap a photo of your receipt, and the app will automatically extract the amount, date, vendor, and currency.
*   **Tech:** Google ML Kit (Text Recognition), Android CameraX API for high-quality image capture, and custom parsing logic for receipt data.

<img width="1344" height="2992" alt="Screenshot_20260715_124327" src="https://github.com/user-attachments/assets/7fc5883e-1b91-4933-95b3-b686f8677823" />
*Figure 2: Automated data extraction from physical receipts using Machine Learning.*

### 3. Advanced Budgeting System
Set strict or flexible financial goals across different timeframes—Daily, Weekly, Monthly, or Yearly. The app provides a "Global Budget" fallback to ensure you never overspend, even without specific plans.
*   **Tech:** Complex SQLite logic with date-range overlaps and automated budget distribution algorithms.

<img width="1344" height="2992" alt="Screenshot_20260715_124632" src="https://github.com/user-attachments/assets/18df1190-b505-46dd-ac60-2b1d39080c15" />
*Figure 3: Setting and tracking multi-period financial goals.*

### 4. Interactive Financial Analytics
Visualize your spending patterns through professional-grade charts. Analyze expenses by category over time to identify where your money goes.
*   **Tech:** MPAndroidChart integration with custom styling and dynamic data filtering.

<img width="1344" height="2992" alt="Screenshot_20260715_124714" src="https://github.com/user-attachments/assets/af814a53-e984-48db-928c-e98a4c266578" />
*Figure 4: Detailed spending analytics and category distribution charts.*

### 5. Automated Recurring Incomes
Automate your regular income streams. Whether it's a monthly salary or weekly freelance pay, the app schedules and reconciles these entries automatically.
*   **Tech:** Custom background reconciliation logic that materializes future occurrences based on user-defined frequency rules.

<img width="1344" height="2992" alt="Screenshot_20260715_124644" src="https://github.com/user-attachments/assets/c62fc270-cee7-4ba9-989f-ae2d151e9fbb" />
*Figure 5: Managing automated recurring income rules.*

### 6. Secure User Profiles & Authentication
Full user lifecycle management including secure registration, login with hashed credentials, and customizable profile settings.
*   **Tech:** Session management, password hashing (BCrypt/PBKDF2 style), and local storage of user preferences.

<img width="1344" height="2992" alt="Screenshot_20260715_124725" src="https://github.com/user-attachments/assets/ca0c4252-cbe2-4239-ad73-0558d9116fae" />
<img width="1344" height="2992" alt="Screenshot_20260715_124349" src="https://github.com/user-attachments/assets/c4ff1d35-b7fe-47a2-bb0f-77b229f68023" />
<img width="1344" height="2992" alt="Screenshot_20260715_124354" src="https://github.com/user-attachments/assets/2b58ff8c-8f18-4661-8db6-124393e88390" />


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
