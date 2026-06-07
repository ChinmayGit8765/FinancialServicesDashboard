import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import KpiCard from '../../components/KpiCard.vue'

describe('KpiCard', () => {
  it('shows a shimmer skeleton when loading is true', () => {
    const wrapper = mount(KpiCard, {
      props: {
        label: 'Market Value',
        primary: '$1,234,567',
        loading: true,
      },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('$1,234,567')
  })

  it('shows the primary value when loaded', () => {
    const wrapper = mount(KpiCard, {
      props: {
        label: 'Market Value',
        primary: '$1,234,567',
        loading: false,
      },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(false)
    expect(wrapper.text()).toContain('$1,234,567')
  })

  it('applies border-up class for positive delta', () => {
    const wrapper = mount(KpiCard, {
      props: { label: 'P&L', primary: '+$45,678', delta: 3.84, loading: false },
    })
    expect(wrapper.find('.border-up').exists()).toBe(true)
    expect(wrapper.find('.kpi-delta').classes()).toContain('kpi-up')
  })

  it('applies border-down class for negative delta', () => {
    const wrapper = mount(KpiCard, {
      props: { label: 'P&L', primary: '-$1,234', delta: -1.12, loading: false },
    })
    expect(wrapper.find('.border-down').exists()).toBe(true)
    expect(wrapper.find('.kpi-delta').classes()).toContain('kpi-down')
  })
})
