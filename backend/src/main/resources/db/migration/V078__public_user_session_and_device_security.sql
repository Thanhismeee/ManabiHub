CREATE TABLE public_user_devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    device_key_hash VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    user_agent VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE UNIQUE INDEX uq_public_user_devices_active 
ON public_user_devices (user_id, device_key_hash) 
WHERE revoked_at IS NULL;

CREATE TABLE public_user_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    device_id UUID NOT NULL REFERENCES public_user_devices(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_public_user_sessions_user_id ON public_user_sessions(user_id);
CREATE INDEX idx_public_user_sessions_device_id ON public_user_sessions(device_id);

CREATE TABLE active_learning_sessions (
    user_id UUID PRIMARY KEY REFERENCES app_users(id),
    public_session_id UUID NOT NULL REFERENCES public_user_sessions(id),
    course_id UUID,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_active_learning_sessions_session_id ON active_learning_sessions(public_session_id);
