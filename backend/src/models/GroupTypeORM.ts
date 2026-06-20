import { 
    Entity, 
    PrimaryGeneratedColumn, 
    Column, 
    CreateDateColumn, 
    OneToMany 
} from 'typeorm';
import { 
    IsNotEmpty, 
    IsDecimal, 
    Min, 
    IsEnum, 
    IsOptional, 
    IsDateString 
} from 'class-validator';
import { Student } from './Student'; // assuming relative reference

export enum GroupType {
    PUBLIC = 'public',
    PRIVATE = 'private'
}

/**
 * TypeORM Entity representation for the "groups" database table.
 * Strictly guarantees and enforces model constraints using TypeORM annotations
 * and robust decorator validations at runtime before persisting payload records.
 */
@Entity({ name: 'groups' })
export class Group {
    @PrimaryGeneratedColumn()
    id!: number;

    @Column({ type: 'varchar', length: 255, nullable: false })
    @IsNotEmpty({ message: 'اسم المجموعة مطلوب ولا يمكن تركه فارغاً.' })
    name!: string;

    @Column({ name: 'start_date', type: 'date', default: () => 'CURRENT_DATE' })
    @IsDateString({}, { message: 'يجب توفير تاريخ بداية صحيح.' })
    startDate!: string;

    @Column({ name: 'monthly_fee', type: 'numeric', precision: 10, scale: 2, default: 0.00 })
    @IsDecimal({}, { message: 'قيمة الرسوم المالية يجب أن تكون قيمة عشرية صحيحة.' })
    @Min(0, { message: 'قيمة الاشتراك لا يمكن أن تكون أقل من صفر.' })
    monthlyFee!: number;

    @Column({ name: 'schedule_days', type: 'varchar', length: 255, nullable: true })
    @IsOptional()
    scheduleDays?: string;

    /**
     * strict app-level enforcement of the 'public' | 'private' option
     * maps directly to the pg string / enum column validation constraint on save operations.
     */
    @Column({
        name: 'group_type',
        type: 'varchar',
        length: 20,
        default: GroupType.PUBLIC,
        nullable: false
    })
    @IsNotEmpty({ message: 'نوع المجموعة (group_type) مطلوب.' })
    @IsEnum(GroupType, { 
        message: "حقل نوع المجموعة غير صالح. يقبل فقط القيم 'public' أو 'private' حصرياً." 
    })
    groupType!: GroupType;

    @CreateDateColumn({ name: 'created_at', type: 'timestamp with time zone' })
    createdAt!: Date;

    // Optional relation back-link
    @OneToMany(() => Student, (student) => student.group, { cascade: true })
    students?: Student[];
}
