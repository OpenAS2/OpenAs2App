import Dashboard from '../../api/dashboard.js';

export default {
    namespaced: true,
    state: {
        tableData: null
    },
    getters: {
    },
    actions: {
        async fetchTableDataByDate({ commit }, payload) {
            try {
                const resp = await Dashboard.getTableDataByDate( payload);
                commit('set_data', resp.data.results);
                return true
            } catch (error) {
                console.log(error)
                return error
            }
        },
        async fetchTableDataByInterval({ commit }, payload) {
            try {
                const resp = await Dashboard.getTableDataByInterval(payload);
                commit('set_data', resp.data.results);
                return true
            } catch (error) {
                console.log(error)
                return error
            }
        }
    },
    mutations: {
        set_data: (state, data) => {
            state.tableData = null;
            state.tableData = data;
        }
    }
}