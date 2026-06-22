package com.example.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.TeacherViewModel
import com.example.data.Group
import com.example.data.Student

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAcademicYearScreen(
    viewModel: TeacherViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(1) }
    
    val groups by viewModel.groups.collectAsState()
    val students by viewModel.students.collectAsState()
    val currentYear by viewModel.currentAcademicYear.collectAsState()
    
    val currentYearLabel = currentYear?.yearLabel ?: "2025/2026"
    val nextYearLabel = remember(currentYearLabel) {
        try {
            val parts = currentYearLabel.split("/")
            if (parts.size == 2) {
                val start = parts[0].toInt() + 1
                val end = parts[1].toInt() + 1
                "$start/$end"
            } else {
                "2026/2027"
            }
        } catch (e: Exception) {
            "2026/2027"
        }
    }
    
    // Step 2 Mapping: Old Group ID -> New Group ID in next year (null means graduate)
    var groupMappings by remember { mutableStateOf<Map<Int, Int?>>(emptyMap()) }
    
    // Automatically set default mapping when groups are loaded
    LaunchedEffect(groups) {
        if (groupMappings.isEmpty() && groups.isNotEmpty()) {
            val sortedGroups = groups.sortedBy { it.name }
            val mappings = mutableMapOf<Int, Int?>()
            for (i in sortedGroups.indices) {
                val currentGroup = sortedGroups[i]
                // Pick next alphabetical group as default, or null if it's the last one
                val nextGroup = if (i + 1 < sortedGroups.size) sortedGroups[i + 1] else null
                mappings[currentGroup.id] = nextGroup?.id
            }
            groupMappings = mappings
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بدء سنة دراسية جديدة", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع", tint = Color(0xFF1B5E20))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF1F8E9))
                .padding(16.dp)
        ) {
            // Step Progress bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepIndicator(step = 1, text = "التأكيد والترقية", isActive = currentStep >= 1, isCurrent = currentStep == 1)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                StepIndicator(step = 2, text = "تعيين المجموعات", isActive = currentStep >= 2, isCurrent = currentStep == 2)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                StepIndicator(step = 3, text = "المعاينة والحفظ", isActive = currentStep >= 3, isCurrent = currentStep == 3)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(modifier = Modifier.weight(1f)) {
                when (currentStep) {
                    1 -> Step1Confirm(
                        currentYearLabel = currentYearLabel,
                        nextYearLabel = nextYearLabel,
                        onNext = { currentStep = 2 }
                    )
                    2 -> Step2GroupSelection(
                        groups = groups,
                        mappings = groupMappings,
                        onMappingsChanged = { groupMappings = it },
                        onNext = { currentStep = 3 },
                        onPrev = { currentStep = 1 }
                    )
                    3 -> Step3Preview(
                        groups = groups,
                        students = students,
                        mappings = groupMappings,
                        nextYearLabel = nextYearLabel,
                        onPrev = { currentStep = 2 },
                        onConfirm = {
                            viewModel.startNewAcademicYear(nextYearLabel, groupMappings) { success ->
                                if (success) {
                                    Toast.makeText(context, "تم بدء السنة الدراسية $nextYearLabel بنجاح وترقية الطلاب!", Toast.LENGTH_LONG).show()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(context, "حدث خطأ أثناء حفظ السنة الدراسية", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StepIndicator(step: Int, text: String, isActive: Boolean, isCurrent: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isCurrent) Color(0xFF2E7D32) else if (isActive) Color(0xFFC8E6C9) else Color(0xFFE0E0E0),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step.toString(),
                    color = if (isCurrent) Color.White else if (isActive) Color(0xFF2E7D32) else Color.DarkGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) Color(0xFF2E7D32) else if (isActive) Color(0xFF2E7D32) else Color.DarkGray
        )
    }
}

@Composable
fun Step1Confirm(
    currentYearLabel: String,
    nextYearLabel: String,
    onNext: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تأكيد بدء السنة الدراسية الجديدة", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 18.sp)
            }
            
            Divider()
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("السنة الحالية: $currentYearLabel", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
                Text("السنة الجديدة المقترحة: $nextYearLabel", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 16.sp)
            }
            
            Surface(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "تحذير هام: سيتم نقل جميع الطلاب النشطين إلى المجموعات المحددة في الخطوة التالية. سيتم اعتبار طلاب مجموعة المستوى الأخير كتخريجين، ولكن مع الحفاظ الكامل على كافة تقارير الحضور والمدفوعات والامتحانات والدرجات كأرشيف تاريخي في النظام.",
                        color = Color(0xFFE65100),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("متابعة لتعيين المجموعات", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun Step2GroupSelection(
    groups: List<Group>,
    mappings: Map<Int, Int?>,
    onMappingsChanged: (Map<Int, Int?>) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("تعيين مجموعات الترقية للطلاب", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 16.sp)
            Text("اختر المجموعة المستهدفة لنقل طلاب كل وعام مجموعة إليها، أو اختر 'متخرج/منقول' إذا كان الطلاب قد أتموا المراحل.", fontSize = 11.sp, color = Color.Gray)
            
            Divider()
            
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (groups.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("لا يوجد مجموعات حالية لترقيتها", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(groups) { group ->
                            var expanded by remember { mutableStateOf(false) }
                            val currentSelectionId = mappings[group.id]
                            val currentSelectionGroup = groups.find { it.id == currentSelectionId }
                            val selectionText = currentSelectionGroup?.name ?: "متخرج / منقول خارج المعهد"
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.2f)) {
                                    Text(group.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1B5E20))
                                    Text("مجموعة حالية", fontSize = 10.sp, color = Color.DarkGray)
                                }
                                
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                
                                Box(modifier = Modifier.weight(1.8f).padding(horizontal = 4.dp)) {
                                    OutlinedButton(
                                        onClick = { expanded = true },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.DarkGray),
                                        modifier = Modifier.fillMaxWidth().height(40.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(selectionText, fontSize = 10.sp, maxLines = 1)
                                    }
                                    
                                    DropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("متخرج / منقول خارج المعهد") },
                                            onClick = {
                                                val newMappings = mappings.toMutableMap()
                                                newMappings[group.id] = null
                                                onMappingsChanged(newMappings)
                                                expanded = false
                                            }
                                        )
                                        groups.forEach { targetGroup ->
                                            DropdownMenuItem(
                                                text = { Text(targetGroup.name) },
                                                onClick = {
                                                    val newMappings = mappings.toMutableMap()
                                                    newMappings[group.id] = targetGroup.id
                                                    onMappingsChanged(newMappings)
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPrev,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("السابق", color = Color.DarkGray)
                }
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("التالي والمعاينة", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun Step3Preview(
    groups: List<Group>,
    students: List<Student>,
    mappings: Map<Int, Int?>,
    nextYearLabel: String,
    onPrev: () -> Unit,
    onConfirm: () -> Unit
) {
    // Generate counts
    val activeStudents = students.filter { it.isActive }
    var totalStudentsToTransfer = 0
    var totalStudentsToGraduate = 0
    
    activeStudents.forEach { student ->
        val mapping = mappings[student.groupId]
        if (mapping == null) {
            totalStudentsToGraduate++
        } else {
            totalStudentsToTransfer++
        }
    }
    
    val totalGroupsWithNoStudentsInNewYear = remember(groups, mappings) {
        val mappedGroupIds = mappings.values.filterNotNull().toSet()
        groups.count { !mappedGroupIds.contains(it.id) }
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("معاينة التغييرات للعام الدراسي الجديد", fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20), fontSize = 16.sp)
            
            Divider()
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatRow(label = "العام الجديد المقترح:", value = nextYearLabel, valueColor = Color(0xFF2E7D32))
                StatRow(label = "عدد الطلاب الذين سيتم ترقيتهم:", value = "$totalStudentsToTransfer طالب", valueColor = Color.Black)
                StatRow(label = "عدد الطلاب الذين سيتم تخرجهم:", value = "$totalStudentsToGraduate طالب", valueColor = Color(0xFFE65100))
                StatRow(label = "مجموعات فارغة جديدة:", value = "$totalGroupsWithNoStudentsInNewYear مجموعات", valueColor = Color.Gray)
            }
            
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "جاهز لحفظ التغييرات: بمجرد الحفظ، سيبدأ النظام فوراً بالتحديث وسيتم أرشفة العام الحالي. يمكنك الانتقال إلى البحث أو ملف الطالب لمطالعة السجلات المتراكمة.",
                        color = Color(0xFF1B5E20),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPrev,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("السابق", color = Color.DarkGray)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Text("تأكيد وحفظ السنة الجديدة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.DarkGray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
