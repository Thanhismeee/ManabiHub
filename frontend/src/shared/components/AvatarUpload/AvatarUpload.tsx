import CameraAltOutlinedIcon from "@mui/icons-material/CameraAltOutlined";
import {Avatar, Box, IconButton, Tooltip} from "@mui/material";

interface AvatarUploadProps {
    avatarUrl: string;
    onSelect?: (file: File) => void;
    disabled?: boolean;
    size?: number;
}

export default function AvatarUpload({
    avatarUrl,
    onSelect,
    disabled = false,
    size = 96,
}: AvatarUploadProps) {

    function handleChange(event: React.ChangeEvent<HTMLInputElement>) {
        const file = event.target.files?.[0];
        if (!file) return;
        onSelect?.(file);
    }

    return (
        <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
            <Box sx={{ position: 'relative' }}>
                <Avatar
                    src={avatarUrl}
                    sx={{
                        width: size,
                        height: size,
                        boxShadow: '0 0 0 4px rgba(196, 30, 58, 0.15), 0 4px 6px -1px rgba(0, 0, 0, 0.1)',
                        bgcolor: '#fef2f2',
                        color: '#C41E3A',
                        fontWeight: 700,
                        fontSize: size > 80 ? '2.5rem' : '2rem'
                    }}
                />
                <Tooltip title="Đổi ảnh đại diện">
                    <IconButton
                        component="label"
                        disabled={disabled}
                        sx={{
                            position: 'absolute',
                            bottom: -4,
                            right: -4,
                            bgcolor: 'white',
                            border: '1px solid #E5E7EB',
                            boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
                            '&:hover': { bgcolor: 'grey.50' },
                            width: size > 80 ? 36 : 30,
                            height: size > 80 ? 36 : 30,
                            color: 'text.secondary',
                        }}
                    >
                        <CameraAltOutlinedIcon sx={{ fontSize: size > 80 ? 20 : 16 }} />
                        <input hidden type="file" accept="image/*" disabled={disabled} onChange={handleChange} />
                    </IconButton>
                </Tooltip>
            </Box>
        </Box>
    );
}
