import {useEffect, useState} from "react";
import {Alert, Box, Button, Card, CardContent, Snackbar, TextField, Grid, Typography, Divider, Stack, MenuItem} from "@mui/material";
import SaveOutlinedIcon from "@mui/icons-material/SaveOutlined";
import LockOutlinedIcon from "@mui/icons-material/LockOutlined";
import VerifiedUserOutlinedIcon from "@mui/icons-material/VerifiedUserOutlined";
import {PageHeader} from "../../shared/components/PageHeader/PageHeader";
import {LoadingState} from "../../shared/components/LoadingState/LoadingState";
import AvatarUpload from "../../shared/components/AvatarUpload/AvatarUpload";
import {avatarUploadErrorMessage, confirmStudentPhoneVerification, getMyStudentProfile, requestStudentPhoneVerification, updateMyStudentProfile, uploadAvatar} from "./profileApi";
import {resolvePublicAssetUrl} from "../../shared/utils/assetUtils";
import {PHONE_PATTERN, sanitizeOtpInput, sanitizePhoneInput} from "./phoneValidation";
import {useNavigate} from "react-router-dom";
import {ROUTES} from "../../shared/constants/routes";
import {getStudentIdentityVerificationStatus, type StudentIdentityVerificationStatus} from "../wallet/services/studentIdentityVerificationService";
import {levelOptions, type LearningLanguage, type ProficiencyFramework} from "../../shared/learning/languageProfiles";
import type {StudentLearningGoal} from "./profileTypes";
import {
    firebasePhoneErrorMessage,
    startFirebasePhoneOtp,
    type FirebasePhoneOtpSession,
} from "../../shared/auth/firebasePhoneAuth";

interface StudentProfileForm {
    avatarUrl: string;
    fullName: string;
    email: string;
    phoneNumber: string;
    displayName: string;
    jlptGoal: string;
    learningGoals: StudentLearningGoal[];
}

const GOAL_CONFIG: Array<{
    language: LearningLanguage;
    framework: ProficiencyFramework;
    label: string;
}> = [
    { language: "KOREAN", framework: "TOPIK", label: "Tiếng Hàn • TOPIK" },
    { language: "CHINESE_SIMPLIFIED", framework: "HSK", label: "Tiếng Trung • HSK" },
    { language: "JAPANESE", framework: "JLPT", label: "Tiếng Nhật • JLPT" },
];

const EMPTY_FORM: StudentProfileForm = {
    avatarUrl: "",
    fullName: "",
    email: "",
    phoneNumber: "",
    displayName: "",
    jlptGoal: "",
    learningGoals: [],
};
const PHONE_OTP_RESEND_COOLDOWN_SECONDS = 60;

export default function StudentProfilePage() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [phoneVerified, setPhoneVerified] = useState(false);
    const [phoneCode, setPhoneCode] = useState("");
    const [phoneOtpSent, setPhoneOtpSent] = useState(false);
    const [phoneOtpCooldownSeconds, setPhoneOtpCooldownSeconds] = useState(0);
    const [firebasePhoneSession, setFirebasePhoneSession] = useState<FirebasePhoneOtpSession | null>(null);
    const [sendingPhoneOtp, setSendingPhoneOtp] = useState(false);
    const [confirmingPhoneOtp, setConfirmingPhoneOtp] = useState(false);
    const [snackbar, setSnackbar] = useState({ open: false, message: "", severity: "success" as "success" | "error" });
    const [identityStatus, setIdentityStatus] = useState<StudentIdentityVerificationStatus | null>(null);
    const [identityStatusLoading, setIdentityStatusLoading] = useState(true);
    const [identityStatusError, setIdentityStatusError] = useState(false);
    const [errors, setErrors] = useState({ fullName: "", phoneNumber: "" });
    const [form, setForm] = useState<StudentProfileForm>(EMPTY_FORM);
    const [initialForm, setInitialForm] = useState<StudentProfileForm>(EMPTY_FORM);

    useEffect(() => {
        loadProfile();
    }, []);

    useEffect(() => () => {
        void firebasePhoneSession?.cancel();
    }, [firebasePhoneSession]);

    useEffect(() => {
        if (phoneOtpCooldownSeconds <= 0) return;
        const timer = window.setTimeout(() => {
            setPhoneOtpCooldownSeconds((seconds) => Math.max(0, seconds - 1));
        }, 1000);
        return () => window.clearTimeout(timer);
    }, [phoneOtpCooldownSeconds]);

    async function loadProfile() {
        setIdentityStatusLoading(true);
        try {
            const [profile, identity] = await Promise.all([
                getMyStudentProfile(),
                getStudentIdentityVerificationStatus().catch(() => null),
            ]);
            setIdentityStatus(identity);
            setIdentityStatusError(identity === null);
            const legacyGoals: StudentLearningGoal[] = profile.jlptGoal
                ? [{
                    learningLanguage: "JAPANESE",
                    proficiencyFramework: "JLPT",
                    frameworkVersion: "CURRENT",
                    proficiencyLevel: profile.jlptGoal,
                    primaryGoal: true,
                }]
                : [];
            const profileData: StudentProfileForm = {
                avatarUrl: profile.avatarUrl ?? "",
                fullName: profile.fullName ?? "",
                email: profile.email ?? "",
                phoneNumber: profile.phoneNumber ?? "",
                displayName: profile.displayName ?? "",
                jlptGoal: profile.jlptGoal ?? "",
                learningGoals: profile.learningGoals?.length ? profile.learningGoals : legacyGoals,
            };
            setForm(profileData);
            setInitialForm(profileData);
            setPhoneVerified(profile.phoneVerified === true);
            setPhoneCode("");
            setPhoneOtpSent(false);
            setPhoneOtpCooldownSeconds(0);
            setFirebasePhoneSession(null);
        } catch (error) {
            console.error(error);
            setSnackbar({ open: true, message: "Không thể tải hồ sơ.", severity: "error" });
        } finally {
            setLoading(false);
            setIdentityStatusLoading(false);
        }
    }

    const isDirty = JSON.stringify(form) !== JSON.stringify(initialForm);

    function validate() {
        const newErrors = { fullName: "", phoneNumber: "" };
        let valid = true;
        if (!form.fullName.trim()) {
            newErrors.fullName = "Họ và tên là bắt buộc.";
            valid = false;
        }
        if (form.phoneNumber && !PHONE_PATTERN.test(form.phoneNumber)) {
            newErrors.phoneNumber = "Số điện thoại không hợp lệ.";
            valid = false;
        }
        setErrors(newErrors);
        return valid;
    }

    async function handleSave() {
        if (!validate()) return;
        if (!phoneVerified && form.phoneNumber !== initialForm.phoneNumber) {
            setSnackbar({ open: true, message: "Hãy xác thực số điện thoại bằng mã SMS trước khi lưu.", severity: "error" });
            return;
        }
        try {
            setSaving(true);
            await updateMyStudentProfile({
                fullName: form.fullName,
                phoneNumber: form.phoneNumber,
                displayName: form.displayName,
                jlptGoal: form.learningGoals.find((goal) =>
                    goal.learningLanguage === "JAPANESE" && goal.proficiencyFramework === "JLPT")?.proficiencyLevel ?? "",
                learningGoals: form.learningGoals,
            });
            await loadProfile();
            setSnackbar({ open: true, message: "Đã cập nhật hồ sơ thành công.", severity: "success" });
        } catch (error: any) {
            console.error(error);
            const response = error.response?.data;
            if (response?.messageCode === "MSG-PRO-002") {
                setErrors((prev) => ({ ...prev, phoneNumber: response.message }));
            }
            setSnackbar({ open: true, message: response?.message ?? "Cập nhật thất bại.", severity: "error" });
        } finally {
            setSaving(false);
        }
    }

    function validatePhone() {
        if (!PHONE_PATTERN.test(form.phoneNumber)) {
            setErrors((prev) => ({ ...prev, phoneNumber: "Số điện thoại không hợp lệ." }));
            return false;
        }
        return true;
    }

    async function handleRequestPhoneOtp() {
        if (phoneVerified || phoneOtpCooldownSeconds > 0 || !validatePhone()) return;
        try {
            setSendingPhoneOtp(true);
            const challenge = await requestStudentPhoneVerification(form.phoneNumber);
            let nextFirebaseSession: FirebasePhoneOtpSession | null = null;
            if (challenge.verificationMethod === "FIREBASE") {
                if (!challenge.challengeId || !challenge.phoneNumberE164) {
                    throw new Error("Backend không trả về challenge Firebase hợp lệ.");
                }
                nextFirebaseSession = await startFirebasePhoneOtp({
                    challengeId: challenge.challengeId,
                    phoneNumberE164: challenge.phoneNumberE164,
                });
            }
            setFirebasePhoneSession(nextFirebaseSession);
            setPhoneOtpSent(true);
            setPhoneOtpCooldownSeconds(PHONE_OTP_RESEND_COOLDOWN_SECONDS);
            setSnackbar({
                open: true,
                message: challenge.verificationMethod === "FIREBASE"
                    ? "Firebase đã gửi SMS thật tới số điện thoại của bạn."
                    : "Đã gửi mã xác thực SMS.",
                severity: "success",
            });
        } catch (error: any) {
            const response = error.response?.data;
            setSnackbar({
                open: true,
                message: response?.message ?? firebasePhoneErrorMessage(error),
                severity: "error",
            });
        } finally {
            setSendingPhoneOtp(false);
        }
    }

    async function handleConfirmPhoneOtp() {
        if (phoneVerified || !validatePhone() || !/^\d{6}$/.test(phoneCode)) {
            setSnackbar({ open: true, message: "Mã xác thực phải gồm 6 chữ số.", severity: "error" });
            return;
        }
        try {
            setConfirmingPhoneOtp(true);
            const proof = firebasePhoneSession
                ? await firebasePhoneSession.confirm(phoneCode).then((firebaseProof) => ({
                    challengeId: firebaseProof.phoneAuthChallengeId,
                    firebaseIdToken: firebaseProof.firebaseIdToken,
                }))
                : { code: phoneCode };
            await confirmStudentPhoneVerification(form.phoneNumber, proof);
            setFirebasePhoneSession(null);
            await loadProfile();
            setSnackbar({ open: true, message: "Số điện thoại đã được xác thực và được khóa thay đổi.", severity: "success" });
        } catch (error: any) {
            const response = error.response?.data;
            setSnackbar({
                open: true,
                message: response?.message ?? firebasePhoneErrorMessage(error),
                severity: "error",
            });
        } finally {
            setConfirmingPhoneOtp(false);
        }
    }

    function handleChange(field: Exclude<keyof StudentProfileForm, "learningGoals">) {
        return (event: React.ChangeEvent<HTMLInputElement>) => {
            const value = field === "phoneNumber"
                ? sanitizePhoneInput(event.target.value)
                : event.target.value;
            setForm({ ...form, [field]: value });
            if (field === "fullName") setErrors((prev) => ({ ...prev, fullName: "" }));
            if (field === "phoneNumber") {
                setErrors((prev) => ({ ...prev, phoneNumber: "" }));
                void firebasePhoneSession?.cancel();
                setFirebasePhoneSession(null);
                setPhoneCode("");
                setPhoneOtpSent(false);
                setPhoneOtpCooldownSeconds(0);
            }
        };
    }

    function updateLearningGoal(
        learningLanguage: LearningLanguage,
        proficiencyFramework: ProficiencyFramework,
        proficiencyLevel: string,
    ) {
        setForm((current) => {
            const withoutLanguage = current.learningGoals.filter((goal) => goal.learningLanguage !== learningLanguage);
            if (!proficiencyLevel) {
                const nextGoals = withoutLanguage.map((goal, index) => ({
                    ...goal,
                    primaryGoal: goal.primaryGoal || (index === 0 && !withoutLanguage.some((item) => item.primaryGoal)),
                }));
                return { ...current, learningGoals: nextGoals };
            }
            const previous = current.learningGoals.find((goal) => goal.learningLanguage === learningLanguage);
            const nextGoal: StudentLearningGoal = {
                learningLanguage,
                proficiencyFramework,
                frameworkVersion: "CURRENT",
                proficiencyLevel,
                primaryGoal: previous?.primaryGoal ?? current.learningGoals.length === 0,
            };
            return { ...current, learningGoals: [...withoutLanguage, nextGoal] };
        });
    }

    function setPrimaryGoal(learningLanguage: LearningLanguage) {
        setForm((current) => ({
            ...current,
            learningGoals: current.learningGoals.map((goal) => ({
                ...goal,
                primaryGoal: goal.learningLanguage === learningLanguage,
            })),
        }));
    }

    async function handleAvatar(file: File) {
        if (file.size > 2 * 1024 * 1024) {
            setSnackbar({ open: true, message: "Kích thước file vượt quá 2MB", severity: "error" });
            return;
        }
        const validTypes = ["image/jpeg", "image/png", "image/webp"];
        if (!validTypes.includes(file.type)) {
            setSnackbar({ open: true, message: "Định dạng không hợp lệ. Chỉ nhận JPEG, PNG, WebP", severity: "error" });
            return;
        }
        const previousAvatarUrl = form.avatarUrl;
        const preview = URL.createObjectURL(file);
        setForm(prev => ({ ...prev, avatarUrl: preview }));
        try {
            setSaving(true);
            const serverUrl = await uploadAvatar(file);
            setForm(prev => ({ ...prev, avatarUrl: serverUrl }));
            // We should also update the profile with the new avatarUrl
            await updateMyStudentProfile({
                fullName: form.fullName,
                phoneNumber: form.phoneNumber,
                displayName: form.displayName,
                jlptGoal: form.learningGoals.find((goal) =>
                    goal.learningLanguage === "JAPANESE" && goal.proficiencyFramework === "JLPT")?.proficiencyLevel ?? "",
                learningGoals: form.learningGoals,
                avatarUrl: serverUrl,
            } as any); // Type cast since updateMyStudentProfile param type might not have avatarUrl yet
            
            setInitialForm(prev => ({ ...prev, avatarUrl: serverUrl }));
            
            setSnackbar({ open: true, message: "Tải ảnh đại diện thành công", severity: "success" });
        } catch (error) {
            console.error("Avatar upload failed:", error);
            setForm(prev => ({ ...prev, avatarUrl: previousAvatarUrl }));
            setSnackbar({ open: true, message: avatarUploadErrorMessage(error), severity: "error" });
        } finally {
            setSaving(false);
            URL.revokeObjectURL(preview);
        }
    }

    if (loading) return <LoadingState fullHeight message="Đang tải hồ sơ..." />;

    return (
        <>
            <Box component="main" sx={{ bgcolor: "#FAF9F6", minHeight: "100vh", py: { xs: 3, md: 5 }, px: { xs: 2, sm: 3 } }}>
                <Box sx={{ maxWidth: '1280px', mx: 'auto', width: '100%', position: 'relative' }}>
                {/* Background Watermark */}
                <Typography variant="h1" sx={{ position: 'absolute', top: -40, right: -20, fontSize: '15rem', fontWeight: 900, color: 'rgba(0,0,0,0.025)', userSelect: 'none', pointerEvents: 'none', zIndex: 0, writingMode: 'vertical-rl' }}>
                  精進
                </Typography>
                <PageHeader
                    title="Hồ sơ cá nhân"
                    subtitle="プロフィール設定"
                    watermark="精進"
                    breadcrumbs={[
                        {label: "Học viên"},
                        {label: "Hồ sơ"},
                    ]}
                />
                
                <Typography color="text.secondary" sx={{ mb: 4, mt: -2 }}>
                    Cập nhật thông tin tài khoản và thiết lập học tập của bạn.
                </Typography>

                <Card elevation={0} sx={{ position: 'relative', zIndex: 1, borderRadius: 4, border: "1px solid", borderColor: 'divider', boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.05)', bgcolor: '#FFFFFF' }}>
                    
                    {/* Hero Profile Header */}
                    <Box sx={{ p: { xs: 3, md: 5 }, display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, alignItems: { xs: 'center', sm: 'flex-start' }, justifyContent: 'space-between', gap: 3 }}>
                        
                        <Box sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, alignItems: 'center', gap: { xs: 2, sm: 4 }, textAlign: { xs: 'center', sm: 'left' } }}>
                            <AvatarUpload
                                avatarUrl={resolvePublicAssetUrl(form.avatarUrl) || ""}
                                onSelect={handleAvatar}
                                disabled={saving}
                                size={80}
                            />
                            <Box>
                                <Typography variant="h5" sx={{ fontWeight: 800, color: 'grey.900', mb: 0.5 }}>
                                    {form.displayName || form.fullName || "Học viên ManabiHub"}
                                </Typography>
                                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                                    {form.email}
                                </Typography>
                                <Typography variant="caption" sx={{ color: 'grey.500', fontWeight: 500, bgcolor: 'grey.50', px: 1.5, py: 0.5, borderRadius: 1 }}>
                                    Ngày tham gia: 24/07/2026
                                </Typography>
                            </Box>
                        </Box>

                        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: { xs: 'center', sm: 'flex-end' }, gap: 1 }}>
                            <Typography variant="caption" sx={{ textTransform: 'uppercase', fontWeight: 700, color: 'grey.500', letterSpacing: 0.5 }}>
                                MỤC TIÊU HIỆN TẠI
                            </Typography>
                            <Box sx={{ px: 2.5, py: 0.75, bgcolor: '#fee2e2', color: '#C41E3A', borderRadius: '9999px', fontWeight: 800, fontSize: '1rem', boxShadow: '0 2px 4px rgba(196, 30, 58, 0.1)' }}>
                                {initialForm.jlptGoal || "Chưa thiết lập"}
                            </Box>
                        </Box>

                    </Box>

                    <Divider sx={{ mx: { xs: 3, md: 5 }, borderColor: 'grey.100' }} />

                    <CardContent sx={{ p: { xs: 3, md: 5 } }}>
                        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 4 }}>
                            <Typography variant="h6" sx={{ fontWeight: 700 }}>
                                Thông tin cá nhân
                            </Typography>
                            {isDirty && (
                                <Typography variant="caption" sx={{ color: '#C41E3A', fontWeight: 700 }}>
                                    * BẠN CÓ THAY ĐỔI CHƯA LƯU
                                </Typography>
                            )}
                        </Box>
                        
                        <Grid container spacing={3}>
                            <Grid size={{ xs: 12, sm: 6 }}>
                                <TextField
                                    fullWidth
                                    label="Họ và tên (*)"
                                    value={form.fullName}
                                    onChange={handleChange("fullName")}
                                    error={!!errors.fullName}
                                    helperText={errors.fullName}
                                    sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
                                />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                                <TextField
                                    fullWidth
                                    label="Tên hiển thị"
                                    value={form.displayName}
                                    onChange={handleChange("displayName")}
                                    placeholder="Tên gọi ngắn gọn..."
                                    sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
                                />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                                <TextField
                                    fullWidth
                                    label="Email"
                                    value={form.email}
                                    disabled
                                    sx={{ bgcolor: 'grey.50' }}
                                    slotProps={{
                                        input: { startAdornment: <Typography sx={{ mr: 1, opacity: 0.5 }}>🔒</Typography> }
                                    }}
                                    helperText="Email đăng nhập - Không thể thay đổi"
                                />
                            </Grid>
                            <Grid size={{ xs: 12, sm: 6 }}>
                                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                                    <TextField
                                        fullWidth
                                        label="Số điện thoại"
                                        value={form.phoneNumber}
                                        disabled={phoneVerified}
                                        type="tel"
                                        onChange={handleChange("phoneNumber")}
                                        error={!!errors.phoneNumber}
                                        helperText={errors.phoneNumber || (phoneVerified ? "Đã xác thực — không thể thay đổi số điện thoại." : "Bạn cần xác thực số điện thoại bằng SMS.")}
                                        slotProps={{
                                            ...(phoneVerified ? { input: { endAdornment: <LockOutlinedIcon fontSize="small" /> } } : {}),
                                            htmlInput: { inputMode: "tel", maxLength: 12, autoComplete: "tel" },
                                        }}
                                        sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
                                    />
                                    {!phoneVerified && (
                                        <Button
                                            variant="outlined"
                                            onClick={handleRequestPhoneOtp}
                                            disabled={saving || sendingPhoneOtp || phoneOtpCooldownSeconds > 0 || !form.phoneNumber}
                                            sx={{ minWidth: 150, height: 56, whiteSpace: "nowrap", borderColor: '#C41E3A', color: '#C41E3A' }}
                                        >
                                            {sendingPhoneOtp
                                                ? "Đang gửi..."
                                                : phoneOtpCooldownSeconds > 0
                                                    ? `Gửi lại sau ${phoneOtpCooldownSeconds}s`
                                                    : phoneOtpSent ? "Gửi lại mã SMS" : "Gửi mã SMS"}
                                        </Button>
                                    )}
                                </Stack>
                                {!phoneVerified && (
                                    <Typography variant="caption" color="text.secondary" sx={{ display: "block", mt: 0.75 }}>
                                        Khi gửi mã, số điện thoại được chuyển tới Google Firebase để chống lạm dụng và gửi SMS; cước SMS tiêu chuẩn có thể áp dụng.
                                    </Typography>
                                )}
                                {!phoneVerified && phoneOtpSent && (
                                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1} sx={{ mt: 1 }}>
                                        <TextField
                                            size="small"
                                            label="Mã SMS 6 số"
                                            value={phoneCode}
                                            onChange={(event) => setPhoneCode(sanitizeOtpInput(event.target.value))}
                                            slotProps={{ htmlInput: { inputMode: "numeric", maxLength: 6 } }}
                                        />
                                        <Button
                                            variant="contained"
                                            onClick={handleConfirmPhoneOtp}
                                            disabled={confirmingPhoneOtp || phoneCode.length !== 6}
                                            startIcon={<VerifiedUserOutlinedIcon />}
                                            sx={{ bgcolor: '#C41E3A', '&:hover': { bgcolor: '#a01830' } }}
                                        >
                                            {confirmingPhoneOtp ? "Đang xác thực..." : "Xác thực"}
                                        </Button>
                                    </Stack>
                                )}
                            </Grid>
                        </Grid>

                        <Divider sx={{ my: 5 }} />

                        <Stack
                            direction={{ xs: "column", sm: "row" }}
                            spacing={2}
                            sx={{
                                alignItems: { sm: "center" },
                                justifyContent: "space-between",
                                p: { xs: 2, sm: 2.5 },
                                borderRadius: 3,
                                border: "1px solid",
                                borderColor: identityStatus?.verified ? "#A7F3D0" : "#BFDBFE",
                                bgcolor: identityStatus?.verified ? "#ECFDF5" : "#EFF6FF",
                            }}
                        >
                            <Box>
                                <Stack direction="row" spacing={1} sx={{ alignItems: "center", mb: 0.5 }}>
                                    <VerifiedUserOutlinedIcon sx={{ color: identityStatus?.verified ? "#059669" : "#2563EB" }} />
                                    <Typography sx={{ fontWeight: 800 }}>
                                        Xác thực CCCD
                                    </Typography>
                                </Stack>
                                <Typography variant="body2" color="text.secondary">
                                    {identityStatusLoading
                                        ? "Đang kiểm tra trạng thái xác thực..."
                                        : identityStatus?.verified
                                            ? `Đã xác thực CCCD thành công${identityStatus.fullName ? ` · ${identityStatus.fullName}` : ""}.`
                                            : identityStatusError
                                                ? "Chưa tải được trạng thái. Bạn có thể mở trang xác thực để kiểm tra lại."
                                                : "Bạn chưa xác thực CCCD. Xác thực để đủ điều kiện rút tiền."}
                                </Typography>
                            </Box>
                            <Button
                                variant={identityStatus?.verified ? "outlined" : "contained"}
                                disabled={identityStatusLoading || identityStatus?.verified === true}
                                onClick={() => navigate(ROUTES.STUDENT.IDENTITY_VERIFICATION)}
                                startIcon={<VerifiedUserOutlinedIcon />}
                                sx={{
                                    flexShrink: 0,
                                    borderRadius: 2,
                                    textTransform: "none",
                                    fontWeight: 800,
                                    ...(identityStatus?.verified
                                        ? { borderColor: "#059669", color: "#047857" }
                                        : { bgcolor: "#2563EB", "&:hover": { bgcolor: "#1D4ED8" } }),
                                }}
                            >
                                {identityStatus?.verified ? "Đã xác thực CCCD thành công" : "Xác thực CCCD"}
                            </Button>
                        </Stack>

                        <Divider sx={{ my: 5 }} />

                                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>
                                    Mục tiêu học ngôn ngữ
                                </Typography>
                                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                                    Có thể đặt mục tiêu riêng cho tiếng Hàn, Trung và Nhật. Chọn một mục tiêu chính để hệ thống ưu tiên lộ trình.
                                </Typography>

                                <Grid container spacing={2} sx={{ mb: 5 }}>
                                    {GOAL_CONFIG.map(({ language, framework, label }) => {
                                        const selectedGoal = form.learningGoals.find((goal) => goal.learningLanguage === language);
                                        return (
                                            <Grid size={{ xs: 12, md: 4 }} key={language}>
                                                <Card variant="outlined" sx={{ height: '100%', borderColor: selectedGoal?.primaryGoal ? '#C41E3A' : 'divider' }}>
                                                    <CardContent>
                                                        <Typography sx={{ fontWeight: 800, mb: 1.5 }}>{label}</Typography>
                                                        <TextField
                                                            select
                                                            fullWidth
                                                            size="small"
                                                            label="Cấp độ mục tiêu"
                                                            value={selectedGoal?.proficiencyLevel ?? ""}
                                                            onChange={(event) => updateLearningGoal(language, framework, event.target.value)}
                                                        >
                                                            <MenuItem value="">Chưa đặt mục tiêu</MenuItem>
                                                            {levelOptions(framework).map((level) => (
                                                                <MenuItem key={level.value} value={level.value}>{level.label}</MenuItem>
                                                            ))}
                                                        </TextField>
                                                        <Button
                                                            size="small"
                                                            variant={selectedGoal?.primaryGoal ? "contained" : "text"}
                                                            disabled={!selectedGoal}
                                                            onClick={() => setPrimaryGoal(language)}
                                                            sx={{ mt: 1.5, textTransform: 'none', fontWeight: 700 }}
                                                        >
                                                            {selectedGoal?.primaryGoal ? "Mục tiêu chính" : "Đặt làm mục tiêu chính"}
                                                        </Button>
                                                    </CardContent>
                                                </Card>
                                            </Grid>
                                        );
                                    })}
                                </Grid>

                                <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 2 }}>
                                    <Button
                                        variant="outlined"
                                        size="large"
                                        disabled={saving || !isDirty}
                                        onClick={() => {
                                            setForm(initialForm);
                                            setErrors({ fullName: "", phoneNumber: "" });
                                        }}
                                        sx={{
                                            px: 4,
                                            borderRadius: 2,
                                            textTransform: "none",
                                            fontWeight: 600,
                                            color: 'text.secondary',
                                            borderColor: 'grey.300',
                                            '&:hover': { bgcolor: 'grey.50', borderColor: 'grey.400' }
                                        }}
                                    >
                                        Hủy
                                    </Button>
                                    <Button
                                        variant="contained"
                                        size="large"
                                        disabled={saving || !isDirty}
                                        startIcon={<SaveOutlinedIcon/>}
                                        sx={{
                                            px: 4,
                                            borderRadius: 2,
                                            textTransform: "none",
                                            fontWeight: 600,
                                            bgcolor: '#C41E3A',
                                            '&:hover': { bgcolor: '#a01830' },
                                            '&.Mui-disabled': { bgcolor: 'grey.300', color: 'grey.500' }
                                        }}
                                        onClick={handleSave}
                                    >
                                        {saving ? "Đang lưu..." : "Lưu thay đổi"}
                                    </Button>
                                </Box>

                            </CardContent>
                        </Card>
                </Box>
            </Box>

            <Snackbar
                open={snackbar.open}
                autoHideDuration={3000}
                onClose={() => setSnackbar({ ...snackbar, open: false })}
            >
                <Alert severity={snackbar.severity} variant="filled">
                    {snackbar.message}
                </Alert>
            </Snackbar>
        </>
    );
}
