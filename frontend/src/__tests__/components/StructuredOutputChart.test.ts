import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
// GREEN: StructuredOutputChart.vue created in Plan 06-04.
import StructuredOutputChart from '../../components/ai/StructuredOutputChart.vue'
import type { StructuredChartDto } from '../../api/ai'

const sampleData: StructuredChartDto = {
  title: 'AI-Detected Sector Exposure',
  subtitle: 'Structured output (demo)',
  series: [
    { label: 'Technology', value: 42.5 },
    { label: 'Financials', value: 21.0 },
    { label: 'Energy', value: 14.3 },
  ],
}

describe('StructuredOutputChart', () => {
  it('shows shimmer skeleton when loading=true', () => {
    const wrapper = mount(StructuredOutputChart, {
      props: { structured: null, loading: true, error: null },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('renders title text and VChart when populated', () => {
    const wrapper = mount(StructuredOutputChart, {
      props: { structured: sampleData, loading: false, error: null },
    })
    // Title must render
    expect(wrapper.text()).toContain('AI-Detected Sector Exposure')
    // VChart stub must be present (stubbed in setup.ts)
    expect(wrapper.findComponent({ name: 'VChart' }).exists()).toBe(true)
  })

  it('renders subtitle when provided', () => {
    const wrapper = mount(StructuredOutputChart, {
      props: { structured: sampleData, loading: false, error: null },
    })
    expect(wrapper.text()).toContain('Structured output (demo)')
  })

  it('shows error state with role="alert" and retry button when error is set', () => {
    const wrapper = mount(StructuredOutputChart, {
      props: { structured: null, loading: false, error: 'Failed to load' },
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
  })

  it('emits retry when retry button is clicked in error state', async () => {
    const wrapper = mount(StructuredOutputChart, {
      props: { structured: null, loading: false, error: 'Failed to load' },
    })
    await wrapper.find('.retry-btn').trigger('click')
    expect(wrapper.emitted('retry')).toBeTruthy()
  })

  it('shows empty state when structured data has no series', () => {
    const empty: StructuredChartDto = { title: 'Empty', series: [] }
    const wrapper = mount(StructuredOutputChart, {
      props: { structured: empty, loading: false, error: null },
    })
    expect(wrapper.find('.chart-empty').exists()).toBe(true)
  })

  it('does not render VChart when loading', () => {
    const wrapper = mount(StructuredOutputChart, {
      props: { structured: sampleData, loading: true, error: null },
    })
    // Should show skeleton, not chart
    expect(wrapper.find('.skeleton').exists()).toBe(true)
    expect(wrapper.findComponent({ name: 'VChart' }).exists()).toBe(false)
  })
})
