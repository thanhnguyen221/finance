# Finance Manager

A comprehensive, high-performance Android application designed to help users take full control of their personal finances. This app combines traditional expense tracking with modern AI-powered features like OCR receipt scanning and automated recurring income management.

## 🚀 Key Features & Visual Tour

### 1. Smart Dashboard & Financial Overview
Get a high-level summary of your financial health at a glance. The dashboard aggregates data from all accounts to show total balance, recent transactions, and budget progress.
*   **Tech:** Custom SQL aggregation queries, efficient data binding, and real-time UI updates.

![Dashboard Overview](DIRECT_IMAGE_URL_HERE)
*Figure 1: Main Dashboard showing real-time financial summaries.*

### 2. AI-Powered OCR Receipt Scanning
Stop manual entry for physical purchases. Simply snap a photo of your receipt, and the app will automatically extract the amount, date, vendor, and currency.
*   **Tech:** Google ML Kit (Text Recognition), Android CameraX API for high-quality image capture, and custom parsing logic for receipt data.

![OCR Receipt Scanning](DIRECT_IMAGE_URL_HERE)
*Figure 2: Automated data extraction from physical receipts using Machine Learning.*

### 3. Advanced Budgeting System
Set strict or flexible financial goals across different timeframes—Daily, Weekly, Monthly, or Yearly. The app provides a "Global Budget" fallback to ensure you never overspend, even without specific plans.
*   **Tech:** Complex SQLite logic with date-range overlaps and automated budget distribution algorithms.

![Budget Management](DIRECT_IMAGE_URL_HERE)
*Figure 3: Setting and tracking multi-period financial goals.*

### 4. Interactive Financial Analytics
Visualize your spending patterns through professional-grade charts. Analyze expenses by category over time to identify where your money goes.
*   **Tech:** MPAndroidChart integration with custom styling and dynamic data filtering.

![Financial Statistics](DIRECT_IMAGE_URL_HERE)
*Figure 4: Detailed spending analytics and category distribution charts.*

### 5. Automated Recurring Incomes
Automate your regular income streams. Whether it's a monthly salary or weekly freelance pay, the app schedules and reconciles these entries automatically.
*   **Tech:** Custom background reconciliation logic that materializes future occurrences based on user-defined frequency rules.

![Recurring Incomes](DIRECT_IMAGE_URL_HERE)
*Figure 5: Managing automated recurring income rules.*

### 6. Transaction Management & Smart Categorization
Detailed logging for every transaction with support for notes, custom categories, and status tracking (e.g., voiding or reconciliation).
*   **Tech:** Robust SQLite database with foreign key constraints, indexing for performance, and ACID-compliant transactions.

![Transaction Management](DIRECT_IMAGE_URL_HERE)
*Figure 6: Comprehensive list and management of historical transactions.*

### 7. Secure User Profiles & Authentication
Full user lifecycle management including secure registration, login with hashed credentials, and customizable profile settings.
*   **Tech:** Session management, password hashing (BCrypt/PBKDF2 style), and local storage of user preferences.

![User Profile](DIRECT_IMAGE_URL_HERE)
*Figure 7: Secure user authentication and profile personalization.*

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
