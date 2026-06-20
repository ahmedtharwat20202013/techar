const express = require('express');
const cron = require('node-cron');
const db = require('./db');
require('dotenv').config();

const app = express();
app.use(express.json());

// =========================================================================
// INTERNALLY MAPPED MONTH NAMES UTILITY (ARABIC & ENGLISH)
// =========================================================================
const ARABIC_MONTHS = {
    "01": "يناير", "02": "فبراير", "03": "مارس", "04": "أبريل",
    "05": "مايو", "06": "يونيو", "07": "يوليو", "08": "أغسطس",
    "09": "سبتمبر", "10": "أكتوبر", "11": "نوفمبر", "12": "ديسمبر"
};

const ENGLISH_MONTHS = {
    "01": "January", "02": "February", "03": "March", "04": "April",
    "05": "May", "06": "June", "07": "July", "08": "August",
    "09": "September", "10": "October", "11": "November", "12": "December"
};

/**
 * Utility to format Arabic payment details
 */
function formatArabicPaymentDescription(paidAtDate, monthStr) {
    try {
        const [year, month] = monthStr.split('-');
        const monthName = ARABIC_MONTHS[month] || month;
        
        const dateObj = new Date(paidAtDate);
        const day = dateObj.getDate();
        const formattedMonth = ARABIC_MONTHS[String(dateObj.getMonth() + 1).padStart(2, '0')] || (dateObj.getMonth() + 1);
        const yearNum = dateObj.getFullYear();
        const paymentDateText = `${day} ${formattedMonth} ${yearNum}`;
        
        return `تم الدفع يوم [${paymentDateText}] لشهر [${monthName} ${year}]`;
    } catch (e) {
        return `تم دفع الاشتراك الشهري`;
    }
}

/**
 * Utility to format English payment details
 */
function formatEnglishPaymentDescription(paidAtDate, monthStr) {
    try {
        const [year, month] = monthStr.split('-');
        const monthName = ENGLISH_MONTHS[month] || month;
        
        const dateObj = new Date(paidAtDate);
        const day = String(dateObj.getDate()).padStart(2, '0');
        const formattedMonth = String(dateObj.getMonth() + 1).padStart(2, '0');
        const yearNum = dateObj.getFullYear();
        const paymentDateText = `${yearNum}-${formattedMonth}-${day}`;
        
        return `Paid on [${paymentDateText}] for the month of [${monthName} ${year}]`;
    } catch (e) {
        return `Monthly subscription paid`;
    }
}

/**
 * Utility to calculate all months inclusive between a starting date and ending date
 */
function getMonthsBetween(startStr, endStr) {
    const start = new Date(startStr);
    const end = new Date(endStr);
    
    let startYear = start.getFullYear();
    let startMonth = start.getMonth(); // 0-11
    
    const endYear = end.getFullYear();
    const endMonth = end.getMonth(); // 0-11
    
    const results = [];
    
    let currentYear = startYear;
    let currentMonth = startMonth;
    
    while (currentYear < endYear || (currentYear === endYear && currentMonth <= endMonth)) {
        const monthNum = String(currentMonth + 1).padStart(2, '0');
        const value = `${currentYear}-${monthNum}`;
        
        // Arabic label
        const arMonthName = ARABIC_MONTHS[monthNum] || monthNum;
        const labelAr = `${arMonthName} ${currentYear}`;
        
        // English label
        const enMonthName = ENGLISH_MONTHS[monthNum] || monthNum;
        const labelEn = `${enMonthName} ${currentYear}`;
        
        results.push({ value, label: labelAr, labelEn });
        
        currentMonth++;
        if (currentMonth > 11) {
            currentMonth = 0;
            currentYear++;
        }
    }
    return results;
}

/**
 * SPECIFICATION 2: PRO-RATA FEE CALCULATION FORMULA
 * Formula: (Full Monthly Fee / Total Days in Current Month) * Remaining Days (including joining day)
 */
function calculateProRataFee(fullMonthlyFee, dateObj) {
    const year = dateObj.getFullYear();
    const month = dateObj.getMonth(); // 0-11
    
    // Get total days in the current calendar month
    const totalDaysInMonth = new Date(year, month + 1, 0).getDate();
    
    // Remaining days (including the joining day)
    const currentDay = dateObj.getDate();
    const remainingDays = totalDaysInMonth - currentDay + 1;
    
    const proRated = (fullMonthlyFee / totalDaysInMonth) * remainingDays;
    return parseFloat(proRated.toFixed(2));
}


// =========================================================================
// REQUIRED CRON JOB: GENERATES MONTHLY RECORDS ON DAY 1 00:00
// Runs strictly on public groups only, skipping private groups.
// =========================================================================
cron.schedule('0 0 1 * *', async () => {
    console.log('Running Automatic Monthly Payments Initialization Cron Job...');
    
    // Get current calendar local time formatted to YYYY-MM
    const now = new Date();
    const currentMonthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    
    try {
        await db.executeTransaction(async (client) => {
            // SPECIFICATION 2: ONLY fetch active students inside PUBLIC groups
            const activeStudentsQuery = `
                SELECT s.id AS student_id, g.monthly_fee 
                FROM students s
                JOIN groups g ON s.group_id = g.id
                WHERE s.is_active = TRUE AND g.group_type = 'public'
            `;
            const { rows: students } = await client.query(activeStudentsQuery);
            
            console.log(`Initializing payments for ${students.length} active public group students for month [${currentMonthStr}]`);
            
            for (const student of students) {
                // Insert a new payment record, skipping if already created to ensure strict idempotency
                const insertQuery = `
                    INSERT INTO monthly_payments (student_id, month, amount_charged, is_paid, paid_at)
                    VALUES ($1, $2, $3, FALSE, NULL)
                    ON CONFLICT (student_id, month) DO NOTHING
                `;
                await client.query(insertQuery, [student.student_id, currentMonthStr, student.monthly_fee]);
            }
        });
        console.log('Monthly payments initialized successfully for public groups.');
    } catch (err) {
        console.error('Error in Monthly Cash Initialization Cron Job:', err.message);
    }
}, {
    timezone: "Africa/Cairo" // Cairo local school year schedule locked context
});


// =========================================================================
// ENDPOINT: ADD NEW STUDENT WITH MID-MONTH PRO-RATA PAYMENT SEEDING
// =========================================================================
app.post('/api/students', async (req, res) => {
    const { group_id, name, parent_phone } = req.body;
    
    if (!name || !group_id) {
        return res.status(400).json({ 
            success: false, 
            message: 'اسم الطالب واختيار المجموعة مطلوبان.' 
        });
    }

    try {
        // Fetch group billing configurations
        const groupRes = await db.query('SELECT monthly_fee, group_type, start_date FROM groups WHERE id = $1', [group_id]);
        if (groupRes.rows.length === 0) {
            return res.status(404).json({ success: false, message: 'عفواً، هذه المجموعة غير موجودة.' });
        }
        
        const { monthly_fee, group_type } = groupRes.rows[0];
        
        const result = await db.executeTransaction(async (client) => {
            // 1. Insert Student profile
            const insertStudentQuery = `
                INSERT INTO students (group_id, name, parent_phone, is_active)
                VALUES ($1, $2, $3, TRUE)
                RETURNING *
            `;
            const studentResult = await client.query(insertStudentQuery, [group_id, name, parent_phone]);
            const student = studentResult.rows[0];
            
            // 2. Compute Billing Card for current month immediately
            const now = new Date();
            const currentMonthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
            
            let amountCharged = parseFloat(monthly_fee) || 0;
            let prDetail = null;
            
            // Apply strict Mid-Month Pro-rata formula ONLY for public groups
            if (group_type === 'public') {
                amountCharged = calculateProRataFee(amountCharged, now);
                prDetail = {
                    formula: "(Full Monthly Fee / Total Days in Current Month) * Remaining Days",
                    pro_rated_amount: amountCharged,
                    full_amount: parseFloat(monthly_fee)
                };
            }
            
            // 3. Immediately generate monthly_payments card
            const insertPaymentQuery = `
                INSERT INTO monthly_payments (student_id, month, amount_charged, is_paid, paid_at)
                VALUES ($1, $2, $3, FALSE, NULL)
                ON CONFLICT (student_id, month) DO NOTHING
                RETURNING *
            `;
            const paymentResult = await client.query(insertPaymentQuery, [student.id, currentMonthStr, amountCharged]);
            
            return {
                student,
                payment: paymentResult.rows[0] || null,
                pro_rata_info: prDetail
            };
        });

        return res.status(201).json({
            success: true,
            message: 'تم إضافة الطالب وتوليد مطالبة الدفع المستحقة بنجاح.',
            data: result
        });
        
    } catch (error) {
        console.error('Error adding student with billing:', error);
        return res.status(500).json({ success: false, message: 'فشل في حفظ بيانات الطالب والاشتراك المالي.' });
    }
});


// =========================================================================
// REQUIRED ENDPOINT 3: CONFIRM CASH PAYMENT API (/api/pay-cash & /api/payments/confirm)
// =========================================================================
const handleConfirmPayment = async (req, res) => {
    const { student_id, studentId, month } = req.body;
    const finalStudentId = student_id || studentId;
    
    if (!finalStudentId || !month) {
        return res.status(400).json({ 
            success: false, 
            message: 'الحقول المطلوبة مفقودة. يرجى توفير كود الطالب والشهر بتنسيق YYYY-MM.' 
        });
    }

    try {
        const result = await db.executeTransaction(async (client) => {
            // Check student existence
            const studentCheck = await client.query('SELECT id, name FROM students WHERE id = $1', [finalStudentId]);
            if (studentCheck.rows.length === 0) {
                throw new Error('ST_NOT_FOUND');
            }

            // Retrieve or preserve existing charged fee context
            const payCheck = await client.query(
                'SELECT amount_charged FROM monthly_payments WHERE student_id = $1 AND month = $2',
                [finalStudentId, month]
            );
            
            let targetFee = 0;
            if (payCheck.rows.length > 0) {
                targetFee = payCheck.rows[0].amount_charged;
            } else {
                // Back-fallback fee query
                const groupFeeQuery = `
                    SELECT g.monthly_fee 
                    FROM students s
                    JOIN groups g ON s.group_id = g.id
                    WHERE s.id = $1
                `;
                const feeRes = await client.query(groupFeeQuery, [finalStudentId]);
                targetFee = feeRes.rows.length > 0 ? feeRes.rows[0].monthly_fee : 0;
            }

            // Update or upsert transactionally
            const updatePaymentQuery = `
                INSERT INTO monthly_payments (student_id, month, amount_charged, is_paid, paid_at)
                VALUES ($1, $2, $3, TRUE, CURRENT_TIMESTAMP)
                ON CONFLICT (student_id, month) 
                DO UPDATE SET is_paid = TRUE, paid_at = CURRENT_TIMESTAMP
                RETURNING *
            `;
            const { rows } = await client.query(updatePaymentQuery, [finalStudentId, month, targetFee]);
            return {
                payment: rows[0],
                student_name: studentCheck.rows[0].name
            };
        });

        const readableAr = formatArabicPaymentDescription(result.payment.paid_at, month);
        const readableEn = formatEnglishPaymentDescription(result.payment.paid_at, month);

        return res.json({
            success: true,
            message: 'تم تسجيل تأكيد وتحصيل الدفعة بنجاح.',
            data: {
                student_id: result.payment.student_id,
                student_name: result.student_name,
                month: result.payment.month,
                amount_charged: result.payment.amount_charged,
                is_paid: result.payment.is_paid,
                paid_at: result.payment.paid_at,
                description: readableAr, // Arabic detail
                description_en: readableEn // English detail ("Paid on [date] for the month of [month name]")
            }
        });

    } catch (error) {
        console.error('Payment Confirmation Error:', error);
        if (error.message === 'ST_NOT_FOUND') {
            return res.status(404).json({ success: false, message: 'عفواً، الطالب غير مسجل بقاعدة البيانات.' });
        }
        return res.status(500).json({ success: false, message: 'فشل في حفظ البيانات. خطأ داخلي بالنظام.' });
    }
};

app.post('/api/pay-cash', handleConfirmPayment);
app.post('/api/payments/confirm', handleConfirmPayment);


// =========================================================================
// REQUIRED ENDPOINT: GET AVAILABLE MONTHS RANGE (get-available-months)
// Custom-tied per group to eliminate layout corruption.
// =========================================================================
const handleGetAvailableMonths = async (req, res) => {
    let { group_id, groupId } = req.query;
    group_id = group_id || groupId; // Support both styles safely
    
    try {
        let startDateVal = null;
        
        if (group_id) {
            const groupRes = await db.query('SELECT start_date FROM groups WHERE id = $1', [group_id]);
            if (groupRes.rows.length > 0) {
                startDateVal = groupRes.rows[0].start_date;
            }
        }
        
        // Fallback: search earliest historical date
        if (!startDateVal) {
            const minGroupRes = await db.query('SELECT MIN(start_date) as min_date FROM groups');
            if (minGroupRes.rows.length > 0 && minGroupRes.rows[0].min_date) {
                startDateVal = minGroupRes.rows[0].min_date;
            }
        }
        
        if (!startDateVal) {
            const minStudentRes = await db.query('SELECT MIN(created_at) as min_created FROM students');
            if (minStudentRes.rows.length > 0 && minStudentRes.rows[0].min_created) {
                startDateVal = minStudentRes.rows[0].min_created;
            } else {
                startDateVal = new Date();
            }
        }
        
        // Compute range
        const start = new Date(startDateVal);
        const startMonthStr = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, '0')}-01`;
        
        const now = new Date();
        const endMonthStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-01`;
        
        const availableMonths = getMonthsBetween(startMonthStr, endMonthStr);
        const currentMonthVal = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
        
        return res.json({
            success: true,
            default_selected: currentMonthVal,
            data: availableMonths
        });
        
    } catch (error) {
        console.error('Error fetching available months:', error);
        return res.status(500).json({ success: false, message: 'فشل في حساب تفاصيل الشهور المتاحة.' });
    }
};

app.get('/api/get-available-months', handleGetAvailableMonths);
app.get('/api/groups/get-available-months', handleGetAvailableMonths);


// =========================================================================
// REQUIRED ENDPOINT 4: FETCH PAYMENTS REPORT / QUERY WITH VALIDATOR & DESCRIPTIONS
// =========================================================================
app.get('/api/payments/list', async (req, res) => {
    let { month, group_id, groupId } = req.query; // Expects "YYYY-MM"
    group_id = group_id || groupId; // Support both styles
    
    if (!month) {
        return res.status(400).json({ success: false, message: 'يرجى تحديد الشهر المستهدف بالاشتراكات.' });
    }

    try {
        let groupStartDate = null;
        
        if (group_id) {
            const groupRes = await db.query('SELECT start_date FROM groups WHERE id = $1', [group_id]);
            if (groupRes.rows.length > 0) {
                groupStartDate = groupRes.rows[0].start_date;
            }
        }
        
        // REQUIREMENT 3 VALIDATION GATE: IF MONTH IS OLDER THAN GROUP START DATE
        if (groupStartDate) {
            const startDate = new Date(groupStartDate);
            const groupStartMonthStr = `${startDate.getFullYear()}-${String(startDate.getMonth() + 1).padStart(2, '0')}`;
            
            if (month < groupStartMonthStr) {
                console.log(`Validation: month [${month}] is older than group start month [${groupStartMonthStr}]. Returning empty.`);
                return res.json({
                    success: true,
                    month: month,
                    message: 'الشهر المطلوب أقدم من تاريخ بداية هذه المجموعة.',
                    data: []
                });
            }
        }

        // Fetch student list & payment details
        let queryText = `
            SELECT 
                s.id AS student_id,
                s.name AS student_name,
                p.is_paid,
                p.paid_at,
                p.amount_charged,
                p.month
            FROM students s
            LEFT JOIN monthly_payments p ON s.id = p.student_id AND p.month = $1
            WHERE s.is_active = TRUE
        `;
        
        const params = [month];
        
        if (group_id) {
            queryText += ` AND s.group_id = $2`;
            params.push(group_id);
        }
        
        queryText += ` ORDER BY s.name ASC`;
        
        const { rows } = await db.query(queryText, params);
        
        const processedList = rows.map(row => {
            const isPaid = row.is_paid === true;
            return {
                student_id: row.student_id,
                student_name: row.student_name,
                month: month,
                amount_charged: parseFloat(row.amount_charged) || 0.00,
                is_paid: isPaid,
                paid_at: row.paid_at,
                // Requirement English & Arabic detailed descriptions
                status_text: isPaid 
                    ? formatArabicPaymentDescription(row.paid_at, month) 
                    : "لم يتم دفع الاشتراك الشهري بعد",
                status_text_en: isPaid
                    ? formatEnglishPaymentDescription(row.paid_at, month)
                    : "Payment pending"
            };
        });

        return res.json({
            success: true,
            month: month,
            data: processedList
        });

    } catch (error) {
        console.error('Fetch payments error:', error);
        return res.status(500).json({ success: false, message: 'فشل في جلب تقرير الاشتراكات.' });
    }
});


// =========================================================================
// REQUIRED ENDPOINT 5: FETCH DAILY ATTENDANCE WITH PRESENT DEFAULT
// "When opening, if no records exist yet, fallback to present for all active students"
// =========================================================================
app.get('/api/attendance/day', async (req, res) => {
    const { date, group_id, groupId } = req.query; // YYYY-MM-DD
    const finalGroupId = group_id || groupId;
    
    if (!date) {
        return res.status(400).json({ success: false, message: 'تاريخ اليوم مطلوب.' });
    }

    try {
        // Step A: Check if any attendance record has been explicitely saved on this date for this group
        let existQuery = `SELECT 1 FROM attendance WHERE date = $1`;
        let existParams = [date];
        if (finalGroupId) {
            existQuery += ` AND group_id = $2`;
            existParams.push(finalGroupId);
        }
        existQuery += ` LIMIT 1`;
        
        const checkExist = await db.query(existQuery, existParams);
        const hasRecordsSaved = checkExist.rows.length > 0;
        
        let queryText = '';
        let queryParams = [];
        
        if (hasRecordsSaved) {
            // Retrieve actual saved records
            queryText = `
                SELECT 
                    s.id AS student_id,
                    s.name AS student_name,
                    COALESCE(a.status, 'present') AS status
                FROM students s
                LEFT JOIN attendance a ON s.id = a.student_id AND a.date = $1
                WHERE s.is_active = TRUE
            `;
            queryParams = [date];
            if (finalGroupId) {
                queryText += ` AND s.group_id = $2`;
                queryParams.push(finalGroupId);
            }
        } else {
            // SPECIFICATION 4: Fallback to all students as 'present' default if no records exist yet
            queryText = `
                SELECT 
                    id AS student_id,
                    name AS student_name,
                    'present'::varchar AS status
                FROM students
                WHERE is_active = TRUE
            `;
            if (finalGroupId) {
                queryText += ` AND group_id = $1`;
                queryParams.push(finalGroupId);
            }
        }
        
        queryText += ` ORDER BY student_name ASC`;
        const { rows } = await db.query(queryText, queryParams);
        
        return res.json({
            success: true,
            date: date,
            group_id: finalGroupId || null,
            has_records_saved: hasRecordsSaved,
            data: rows
        });
        
    } catch (error) {
        console.error('Fetch daily attendance error:', error);
        return res.status(500).json({ success: false, message: 'فشل في استدعاء سجل الغياب.' });
    }
});


// =========================================================================
// REQUIRED ENDPOINT 6: TOGGLE / UPDATE ATTENDANCE STATUS
// =========================================================================
app.post('/api/attendance/toggle', async (req, res) => {
    const { student_id, date, status } = req.body; // expected status: 'present' or 'absent'
    
    if (!student_id || !date || !status) {
        return res.status(400).json({ success: false, message: 'برجاء التحقق من المدخلات.' });
    }

    if (status !== 'present' && status !== 'absent') {
        return res.status(400).json({ success: false, message: 'حالة الحضور غير صالحة. يجب أن تكون present أو absent.' });
    }

    try {
        // Query group_id of student transactionally to store with attendance card
        const studentRes = await db.query('SELECT group_id FROM students WHERE id = $1', [student_id]);
        if (studentRes.rows.length === 0) {
            return res.status(404).json({ success: false, message: 'الطالب غير مسجل بقاعدة البيانات.' });
        }
        const groupId = studentRes.rows[0].group_id;

        const saveQuery = `
            INSERT INTO attendance (student_id, group_id, date, status)
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (student_id, date) 
            DO UPDATE SET status = EXCLUDED.status
            RETURNING *
        `;
        const { rows } = await db.query(saveQuery, [student_id, groupId, date, status]);
        
        return res.json({
            success: true,
            message: 'تم حفظ حالة الحضور بنجاح.',
            data: rows[0]
        });
    } catch (error) {
        console.error('Toggle attendance error:', error);
        return res.status(500).json({ success: false, message: 'فشل في تعديل حالة الغياب.' });
    }
});


// =========================================================================
// REQUIRED ENDPOINT 7: RETRIEVE MONTHLY STATS PER STUDENT (AGGREGATION REPORT)
// =========================================================================
app.get('/api/attendance/stats/:studentId', async (req, res) => {
    const { studentId } = req.params;
    const { month } = req.query; // Expected: YYYY-MM
    
    if (!month) {
        return res.status(400).json({ success: false, message: 'يرجى تحديد الشهر (YYYY-MM) لحساب المؤشرات.' });
    }

    try {
        // Query both present and absent records for specified month
        const queryText = `
            SELECT 
                COUNT(*) FILTER (WHERE status = 'present') AS present_count,
                COUNT(*) FILTER (WHERE status = 'absent') AS absent_count,
                COUNT(*) AS total_classes
            FROM attendance
            WHERE student_id = $1 
            AND TO_CHAR(date, 'YYYY-MM') = $2
        `;
        const { rows } = await db.query(queryText, [studentId, month]);
        const stats = rows[0];

        const total = parseInt(stats.total_classes) || 0;
        const present = parseInt(stats.present_count) || 0;
        const absent = parseInt(stats.absent_count) || 0;
        
        const attendanceRate = total > 0 ? parseFloat(((present / total) * 100).toFixed(1)) : 100;

        return res.json({
            success: true,
            student_id: studentId,
            month: month,
            stats: {
                total_sessions: total,
                present_sessions: present,
                absent_sessions: absent,
                attendance_rate_percentage: attendanceRate
            }
        });
    } catch (error) {
        console.error('Fetch attendance stats error:', error);
        return res.status(500).json({ success: false, message: 'فشل في استخراج إحصائيات الغياب.' });
    }
});


// =========================================================================
// REGISTER SAMPLES & TESTING DEMO DATA ENFORCER
// =========================================================================
app.post('/api/demo/initialize', async (req, res) => {
    try {
        await db.executeTransaction(async (client) => {
            // Clean all tables including groups cascade style
            await client.query('TRUNCATE TABLE attendance, monthly_payments, students, groups RESTART IDENTITY CASCADE');
            
            // Insert demo groups with distinct starting dates to test month filter ranges
            const grpQuery = `
                INSERT INTO groups (name, start_date, monthly_fee, schedule_days, group_type)
                VALUES 
                ('المجموعة أ (تأسيس)', '2026-05-10', 150.00, 'الإثنين، الأربعاء', 'public'),
                ('المجموعة ب (متقدم)', '2026-06-01', 200.00, 'السبت، الثلاثاء', 'private')
                RETURNING id, name, start_date, group_type
            `;
            const { rows: groups } = await client.query(grpQuery);
            
            // Insert student profiles linked to specific groups
            const stQuery = `
                INSERT INTO students (group_id, name, parent_phone) 
                VALUES 
                ($1, 'أحمد خالد محمد', '01012345678'),
                ($1, 'سارة يوسف علي', '01198765432'),
                ($2, 'محمود عبد الرحمن', '01234567890')
                RETURNING id, name, group_id
            `;
            const { rows: students } = await client.query(stQuery, [groups[0].id, groups[1].id]);
            
            // Insert some payment history for the current month
            const currentMonthStr = `${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}`;
            const previousMonthStr = "2026-05";
            
            // Group A student 1 paid previous and current month
            await client.query(`
                INSERT INTO monthly_payments (student_id, month, amount_charged, is_paid, paid_at)
                VALUES ($1, $2, 150.00, TRUE, CURRENT_TIMESTAMP), ($1, $3, 150.00, TRUE, CURRENT_TIMESTAMP)
            `, [students[0].id, currentMonthStr, previousMonthStr]);
            
            // Group A student 2 unpaid for both
            await client.query(`
                INSERT INTO monthly_payments (student_id, month, amount_charged, is_paid)
                VALUES ($1, $2, 150.00), ($1, $3, 150.00)
            `, [students[1].id, currentMonthStr, previousMonthStr]);

            // Group B Student (only active from June 2026 onwards) - paid June
            await client.query(`
                INSERT INTO monthly_payments (student_id, month, amount_charged, is_paid, paid_at)
                VALUES ($1, $2, 200.00, TRUE, CURRENT_TIMESTAMP)
            `, [students[2].id, currentMonthStr]);

            console.log('Demo configuration with groups and relational students populated successfully.');
        });
        
        return res.json({ success: true, message: 'تم إعداد وتهيئة قاعدة البيانات بالمجموعات وببيانات تجريبية بنجاح.' });
    } catch (e) {
        console.error(e);
        return res.status(500).json({ success: false, message: 'خطأ أثناء تهيئة البيانات التجريبية.' });
    }
});


// Start server listener
const PORT = process.env.PORT || 8080;
app.listen(PORT, () => {
    console.log(`Teacher Assistant System Server successfully initialized and active on port ${PORT}`);
});
