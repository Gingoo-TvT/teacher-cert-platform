<script setup lang="ts">
import { dateZhCN, zhCN, type GlobalThemeOverrides } from 'naive-ui'

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#1f6feb',
    primaryColorHover: '#388bfd',
    primaryColorPressed: '#1657c4',
    borderRadius: '6px',
    borderRadiusSmall: '4px',
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", "PingFang SC", Roboto, Helvetica, Arial, sans-serif'
  },
  Card: {
    borderRadius: '8px'
  },
  Button: {
    borderRadiusMedium: '6px'
  }
}
</script>

<template>
  <n-config-provider :theme-overrides="themeOverrides" :locale="zhCN" :date-locale="dateZhCN">
    <n-loading-bar-provider>
      <n-message-provider>
        <n-dialog-provider>
          <n-notification-provider>
            <router-view />
          </n-notification-provider>
        </n-dialog-provider>
      </n-message-provider>
    </n-loading-bar-provider>
  </n-config-provider>
</template>
