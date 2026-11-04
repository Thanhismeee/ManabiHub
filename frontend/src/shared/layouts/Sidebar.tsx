import React, { useMemo } from 'react';
import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { useLocale } from '../i18n/locale';

export const DRAWER_WIDTH = 280;
export const COLLAPSED_DRAWER_WIDTH = 72;

export interface MenuItem {
  title: string;
  path: string;
  icon: React.ElementType;
  roles?: string[];
}

interface SidebarProps {
  menuItems: MenuItem[];
  open: boolean;
  collapsed?: boolean;
  onClose?: () => void;
  userRoles: string[];
  variant?: 'permanent' | 'temporary';
}

export const Sidebar: React.FC<SidebarProps> = ({
  collapsed = false,
  menuItems,
  onClose,
  open,
  userRoles,
  variant = 'permanent',
}) => {
  const location = useLocation();
  const { t } = useLocale();
  const navigate = useNavigate();

  const visibleMenuItems = useMemo(() => {
    return menuItems.filter(item => {
      if (!item.roles || item.roles.length === 0) return true;
      return item.roles.some((role) => userRoles.includes(role));
    });
  }, [menuItems, userRoles]);

  const content = (
    <Box sx={{ overflowX: 'hidden', overflowY: 'auto', mt: 2 }}>
      <List>
        {visibleMenuItems.map((item) => {
          const isSelected = location.pathname.startsWith(item.path);
          return (
            <ListItem key={item.path} disablePadding sx={{ display: 'block', mb: 0.5 }}>
              <Tooltip title={collapsed ? t(item.title) : ''} placement="right">
                <ListItemButton
                  aria-label={t(item.title)}
                  selected={isSelected}
                  onClick={() => {
                    navigate(item.path);
                    if (variant === 'temporary' && onClose) {
                      onClose();
                    }
                  }}
                  sx={{
                    borderRadius: 2,
                    justifyContent: collapsed ? 'center' : 'initial',
                    minHeight: 48,
                    mx: collapsed ? 1 : 2,
                    px: collapsed ? 1.5 : 3,
                    transition: (theme) => theme.transitions.create(['margin', 'padding'], {
                      duration: theme.transitions.duration.shorter,
                    }),
                    '&.Mui-selected': {
                      bgcolor: 'primary.main',
                      color: 'primary.contrastText',
                      '&:hover': {
                        bgcolor: 'primary.dark',
                      },
                      '& .MuiListItemIcon-root': {
                        color: 'primary.contrastText',
                      },
                    },
                  }}
                >
                  <ListItemIcon
                    sx={{
                      color: isSelected ? 'inherit' : 'text.secondary',
                      justifyContent: 'center',
                      minWidth: collapsed ? 0 : 40,
                      mr: collapsed ? 0 : 1,
                    }}
                  >
                    <item.icon />
                  </ListItemIcon>
                  <ListItemText
                    primary={<Typography sx={{ fontWeight: isSelected ? 600 : 500, fontSize: '0.9rem' }}>{t(item.title)}</Typography>}
                    sx={{
                      opacity: collapsed ? 0 : 1,
                      overflow: 'hidden',
                      whiteSpace: 'nowrap',
                      width: collapsed ? 0 : 'auto',
                    }}
                  />
                </ListItemButton>
              </Tooltip>
            </ListItem>
          );
        })}
      </List>
    </Box>
  );

  return (
    <Drawer
      variant={variant}
      open={open}
      onClose={onClose}
      ModalProps={{ keepMounted: true }}
      sx={{
        width: variant === 'permanent'
          ? (collapsed ? COLLAPSED_DRAWER_WIDTH : DRAWER_WIDTH)
          : 0,
        flexShrink: 0,
        '& .MuiDrawer-paper': {
          overflowX: 'hidden',
          transition: (theme) => theme.transitions.create('width', {
            duration: theme.transitions.duration.shorter,
            easing: theme.transitions.easing.sharp,
          }),
          width: collapsed ? COLLAPSED_DRAWER_WIDTH : DRAWER_WIDTH,
          boxSizing: 'border-box',
          borderRight: '1px solid',
          borderColor: 'divider',
        },
      }}
    >
      <Toolbar />
      {content}
    </Drawer>
  );
};
