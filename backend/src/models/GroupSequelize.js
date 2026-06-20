const { DataTypes, Model } = require('sequelize');

/**
 * Sequelize Model representation for the "groups" database table.
 * Enforces schema-level design constraints and input rules at the application layer.
 */
class Group extends Model {
    /**
     * Helper validation to run custom model-level assertions on saved payloads
     */
    static associate(models) {
        // One Group has many Students
        Group.hasMany(models.Student, {
            foreignKey: 'group_id',
            as: 'students',
            onDelete: 'CASCADE'
        });
    }
}

module.exports = (sequelize) => {
    Group.init({
        id: {
            type: DataTypes.INTEGER,
            autoIncrement: true,
            primaryKey: true,
            allowNull: false
        },
        name: {
            type: DataTypes.STRING(255),
            allowNull: false,
            validate: {
                notEmpty: {
                    msg: 'اسم المجموعة مطلوب ولا يمكن تركه فارغاً.'
                }
            }
        },
        startDate: {
            type: DataTypes.DATEONLY,
            field: 'start_date',
            defaultValue: DataTypes.NOW,
            allowNull: false,
            validate: {
                isDate: {
                    msg: 'تاريخ بداية المجموعة يجب أن يكون تاريخاً صحيحاً بتنسيق YYYY-MM-DD.'
                }
            }
        },
        monthlyFee: {
            type: DataTypes.DECIMAL(10, 2),
            field: 'monthly_fee',
            defaultValue: 0.00,
            allowNull: false,
            validate: {
                isDecimal: {
                    msg: 'القيمة المالية للاشتراك الشهري يجب أن تكون رقماً عشرياً.'
                },
                min: {
                    args: [0.00],
                    msg: 'لا يمكن أن تكون قيمة الاشتراك الشهري سالبة.'
                }
            }
        },
        scheduleDays: {
            type: DataTypes.STRING(255),
            field: 'schedule_days',
            allowNull: true
        },
        groupType: {
            type: DataTypes.STRING(20),
            field: 'group_type',
            allowNull: false,
            defaultValue: 'public',
            validate: {
                // Application-level enforcement/validation constraint
                isIn: {
                    args: [['public', 'private']],
                    msg: "نوع المجموعة غير صالح. يجب أن يكون إما 'public' أو 'private'."
                }
            }
        }
    }, {
        sequelize,
        modelName: 'Group',
        tableName: 'groups',
        underscored: true,
        timestamps: true,
        createdAt: 'created_at',
        updatedAt: 'updated_at'
    });

    return Group;
};
