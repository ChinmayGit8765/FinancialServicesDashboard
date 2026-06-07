import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TransactionsTable from '../../components/TransactionsTable.vue'
import type { PageResponse, TransactionDto } from '../../api/portfolio'

const makeTx = (txType: 'BUY' | 'SELL', ticker = 'AAPL'): TransactionDto => ({
  txDate: '2024-01-15',
  txType,
  ticker,
  quantity: 10,
  price: 185.5,
  tradeValue: 1855,
  runningCostBasis: 5000,
})

function makePage(transactions: TransactionDto[], pageNum = 0, total = 1): PageResponse<TransactionDto> {
  return {
    content: transactions,
    totalPages: total,
    totalElements: transactions.length,
    number: pageNum,
    size: 10,
  }
}

describe('TransactionsTable', () => {
  it('renders badge-buy class for BUY transactions', () => {
    const page = makePage([makeTx('BUY')])
    const wrapper = mount(TransactionsTable, {
      props: { page, loading: false, error: null },
    })
    expect(wrapper.find('.badge-buy').exists()).toBe(true)
  })

  it('renders badge-sell class for SELL transactions', () => {
    const page = makePage([makeTx('SELL')])
    const wrapper = mount(TransactionsTable, {
      props: { page, loading: false, error: null },
    })
    expect(wrapper.find('.badge-sell').exists()).toBe(true)
  })

  it('renders both badge-buy and badge-sell when mixed types present', () => {
    const page = makePage([makeTx('BUY'), makeTx('SELL', 'MSFT')])
    const wrapper = mount(TransactionsTable, {
      props: { page, loading: false, error: null },
    })
    expect(wrapper.find('.badge-buy').exists()).toBe(true)
    expect(wrapper.find('.badge-sell').exists()).toBe(true)
  })

  it('emits page-change with next page index when Next is clicked', async () => {
    const page = makePage([makeTx('BUY')], 0, 3)
    const wrapper = mount(TransactionsTable, {
      props: { page, loading: false, error: null },
    })
    const buttons = wrapper.findAll('.page-btn')
    const nextBtn = buttons[1]  // Prev is [0], Next is [1]
    await nextBtn.trigger('click')
    expect(wrapper.emitted('page-change')).toBeTruthy()
    expect(wrapper.emitted('page-change')![0]).toEqual([1])
  })

  it('prev button is disabled on first page (number === 0)', () => {
    const page = makePage([makeTx('BUY')], 0, 3)
    const wrapper = mount(TransactionsTable, {
      props: { page, loading: false, error: null },
    })
    const prevBtn = wrapper.findAll('.page-btn')[0]
    expect(prevBtn.attributes('disabled')).toBeDefined()
  })

  it('next button is disabled on last page', () => {
    const page = makePage([makeTx('BUY')], 2, 3)  // page 2 of 3 (0-indexed), last page
    const wrapper = mount(TransactionsTable, {
      props: { page, loading: false, error: null },
    })
    const nextBtn = wrapper.findAll('.page-btn')[1]
    expect(nextBtn.attributes('disabled')).toBeDefined()
  })

  it('shows empty state when no transactions', () => {
    const page = makePage([], 0, 0)
    const wrapper = mount(TransactionsTable, {
      props: { page, loading: false, error: null },
    })
    expect(wrapper.text()).toContain('No transactions recorded.')
  })

  it('shows skeleton rows when loading', () => {
    const wrapper = mount(TransactionsTable, {
      props: { page: null, loading: true, error: null },
    })
    const skeletonRows = wrapper.findAll('tbody tr.skeleton-row')
    expect(skeletonRows).toHaveLength(5)
  })
})
