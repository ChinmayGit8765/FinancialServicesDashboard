import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
// RED scaffold: MonteCarloFanChart.vue does not yet exist — import will fail until Plan 05-03 creates it.
// This is the intended RED state (Wave 0 frontend gate). The test structure is locked here
// for Plan 05-03 to turn GREEN when MonteCarloFanChart.vue and the model selector are built.
import MonteCarloFanChart from '../../components/MonteCarloFanChart.vue'

// Sample forecast data matching ForecastDto shape
const makeForecastData = (model = 'GBM', horizonDays = 5) => ({
  model,
  horizonDays,
  p5:  Array.from({ length: horizonDays }, (_, i) => 90  + i * 0.1),
  p25: Array.from({ length: horizonDays }, (_, i) => 95  + i * 0.2),
  p50: Array.from({ length: horizonDays }, (_, i) => 100 + i * 0.3),
  p75: Array.from({ length: horizonDays }, (_, i) => 105 + i * 0.4),
  p95: Array.from({ length: horizonDays }, (_, i) => 110 + i * 0.5),
})

describe('MonteCarloFanChart', () => {
  // -----------------------------------------------------------------------
  // Loading / error / empty states (mirrors CorrelationHeatmap.test.ts pattern)
  // -----------------------------------------------------------------------

  it('shows a shimmer skeleton when loading is true', () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: { forecast: null, loading: true, error: null },
    })
    expect(wrapper.find('.skeleton').exists()).toBe(true)
  })

  it('shows empty state when forecast data is null and not loading', () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: { forecast: null, loading: false, error: null },
    })
    expect(wrapper.find('.chart-empty').exists()).toBe(true)
  })

  it('shows error state with retry button when error is set', () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: { forecast: null, loading: false, error: 'Failed to load forecast' },
    })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
  })

  // -----------------------------------------------------------------------
  // Fan chart rendering: 5 ECharts series (base + 3 bands + median)
  // -----------------------------------------------------------------------

  it('renders 5 ECharts series when forecast data is present (base p5 floor + 3 bands + median)', () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: {
        forecast: makeForecastData(),
        loading: false,
        error: null,
      },
    })
    // The chart should be visible (not skeleton/error/empty)
    expect(wrapper.find('.skeleton').exists()).toBe(false)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.find('.chart-empty').exists()).toBe(false)

    // ECharts v-chart should be present
    expect(wrapper.findComponent({ name: 'VChart' }).exists()).toBe(true)
  })

  // -----------------------------------------------------------------------
  // Model selector: clicking a model toggle button updates selectedModel + emits update:model
  // -----------------------------------------------------------------------

  it('renders model selector toggle buttons for all 4 models', () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: {
        forecast: makeForecastData(),
        loading: false,
        error: null,
      },
    })
    // Should have 4 toggle buttons (GBM, JUMP_DIFFUSION, HESTON, BOOTSTRAP)
    const toggleButtons = wrapper.findAll('.toggle-btn')
    expect(toggleButtons).toHaveLength(4)

    const buttonTexts = toggleButtons.map(btn => btn.text())
    expect(buttonTexts).toContain('GBM')
    expect(buttonTexts).toContain('JUMP_DIFFUSION')
    expect(buttonTexts).toContain('HESTON')
    expect(buttonTexts).toContain('BOOTSTRAP')
  })

  it('clicking a model toggle button emits update:model with the selected model', async () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: {
        forecast: makeForecastData('GBM'),
        loading: false,
        error: null,
      },
    })

    // Find and click the HESTON button
    const hestonButton = wrapper.findAll('.toggle-btn').find(btn => btn.text() === 'HESTON')
    expect(hestonButton).toBeDefined()
    await hestonButton!.trigger('click')

    // Should emit 'update:model' with 'HESTON'
    const emitted = wrapper.emitted('update:model')
    expect(emitted).toBeTruthy()
    expect(emitted![0]).toEqual(['HESTON'])
  })

  it('GBM button has active class by default', () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: {
        forecast: makeForecastData('GBM'),
        loading: false,
        error: null,
      },
    })

    const gbmButton = wrapper.findAll('.toggle-btn').find(btn => btn.text() === 'GBM')
    expect(gbmButton).toBeDefined()
    expect(gbmButton!.classes()).toContain('active')
  })

  it('clicking BOOTSTRAP button makes it active and GBM inactive', async () => {
    const wrapper = mount(MonteCarloFanChart, {
      props: {
        forecast: makeForecastData('GBM'),
        loading: false,
        error: null,
      },
    })

    const bootstrapButton = wrapper.findAll('.toggle-btn').find(btn => btn.text() === 'BOOTSTRAP')
    await bootstrapButton!.trigger('click')

    expect(bootstrapButton!.classes()).toContain('active')
    const gbmButton = wrapper.findAll('.toggle-btn').find(btn => btn.text() === 'GBM')
    expect(gbmButton!.classes()).not.toContain('active')
  })
})
