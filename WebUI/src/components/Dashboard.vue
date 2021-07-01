<template>
    <div id="dashboard">
      <div  v-if="errored" v-html="errormsg" class="alert alert-primary" role="alert"></div>
      <div class="d-flex justify-content-between flex-wrap flex-md-nowrap align-items-center pt-3 pb-2 mb-3">
        <h1 class="h2">Dashboard</h1>
        <div class="btn-toolbar mb-2 mb-md-0">
          <div class="btn-group mr-2">
            <button type="button" class="btn btn-sm btn-outline-secondary px-9">Share</button>
            <button type="button" class="btn btn-sm btn-outline-secondary px-9">Export</button>
          </div>
          <div class="btn btn-sm btn-outline-secondary selectableWidth">
            <v-row>
              <v-col cols="2" class="align-self-center p-0">
                <svg xmlns="http://www.w3.org/2000/svg" width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feather feather-calendar"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"></rect><line x1="16" y1="2" x2="16" y2="6"></line><line x1="8" y1="2" x2="8" y2="6"></line><line x1="3" y1="10" x2="21" y2="10"></line></svg>
              </v-col>
              <v-col cols="10" class="align-self-center p-0">
                <v-select
                  v-model="weekSelect"
                  :items="items"
                  item-text="name"
                  item-value="value"
                  label="Select Range">
                </v-select>
              </v-col>
            </v-row>
          </div>
        </div>
      </div>
      <div class="d-flex flex-row-reverse align-items-center pt-3 pb-2 mb-3 border-bottom" v-if="weekSelect === 3">
        <div>
          <v-menu
        ref="lastMenu"
        v-model="lastMenu"
        :close-on-content-click="false"
        :return-value.sync="lastDate"
        transition="scale-transition"
        offset-y
        min-width="auto"
      >
        <template v-slot:activator="{ on, attrs }">
          <v-text-field
            v-model="lastDate"
            label="Last Date"
            prepend-icon="mdi-calendar"
            readonly
            v-bind="attrs"
            v-on="on"
          ></v-text-field>
        </template>
        <v-date-picker
          v-model="lastDate"
          no-title
          scrollable
        >
          <v-spacer></v-spacer>
          <v-btn
            text
            color="primary"
            @click="lastMenu = false"
          >
            Cancel
          </v-btn>
          <v-btn
            text
            color="primary"
            @click="$refs.lastMenu.save(lastDate)"
          >
            OK
          </v-btn>
        </v-date-picker>
      </v-menu>
        </div>
        <div>
          <v-menu
        ref="startMenu"
        v-model="startMenu"
        :close-on-content-click="false"
        :return-value.sync="startDate"
        transition="scale-transition"
        offset-y
        min-width="auto"
      >
        <template v-slot:activator="{ on, attrs }">
          <v-text-field
            v-model="startDate"
            label="Start Date"
            prepend-icon="mdi-calendar"
            readonly
            v-bind="attrs"
            v-on="on"
          ></v-text-field>
        </template>
        <v-date-picker
          v-model="startDate"
          no-title
          scrollable
        >
          <v-spacer></v-spacer>
          <v-btn
            text
            color="primary"
            @click="startMenu = false"
          >
            Cancel
          </v-btn>
          <v-btn
            text
            color="primary"
            @click="$refs.startMenu.save(startDate)"
          >
            OK
          </v-btn>
        </v-date-picker>
      </v-menu>
        </div>
      </div>
      <!-- <iframe href="https://sourceforge.net/p/openas2/discussion/" width="100%" height="800px" border="0" style="border:none"></iframe> -->
     <v-tabs
        v-model="tab"
        ref="tabs"
      >
        <v-tab href="#sent">
          Sent
        </v-tab>
        <v-tab href="#received">
          Received
        </v-tab>
        <v-tabs-items :value="tab">
      <v-text-field
        v-model="search"
        label="Search"
        single-line
        hide-details
        class="my-6"
      ></v-text-field>
          <v-tab-item
            value="sent"
            class="mb-10"
            transition="fade-up"
            reverse-transition="fade-up"
          >
          <v-data-table
            :headers="headers"
            :items="filteredData"
            :search="search"
            :sort-desc="true"
            sort-by="name"
            :loading="loading"
            no-data-text="No Data for sent">
              <template v-slot:[`item.msgId`]="props">
                  <v-edit-dialog :return-value.sync="props.item.msgId">
                    <div class="text-truncate fixWidth"> {{ props.item.msgId }}</div>
                    <template v-slot:input>
                      <div class="p-5">{{ props.item.msgId }}</div>
                    </template>
                  </v-edit-dialog>
               </template>
              <template v-slot:[`item.mdnResponse`]="props">
                  <v-edit-dialog :return-value.sync="props.item.mdnResponse">
                    <div class="text-truncate fixWidth"> {{ props.item.mdnResponse }}</div>
                    <template v-slot:input>
                      <div class="p-5">{{ props.item.mdnResponse }}</div>
                    </template>
                  </v-edit-dialog>
               </template>
               <template v-slot:[`item.msgData`]="props">
                  <v-edit-dialog :return-value.sync="props.item.msgData">
                    <div class="text-truncate fixWidth"> {{ props.item.msgData }}</div>
                    <template v-slot:input>
                      <div class="p-5">{{ props.item.msgData }}</div>
                    </template>
                  </v-edit-dialog>
               </template>
          </v-data-table>
          </v-tab-item>
          <v-tab-item
            value="received"
            class="mb-10"
            transition="fade-up"
            reverse-transition="fade-up"
          >
          <v-data-table
            :headers="headers"
            :items="filteredData"
            :search="search"
            :sort-desc="true"
            sort-by="name"
            :loading="loading"
            no-data-text="No Data for receive">
          </v-data-table>
          </v-tab-item>
        </v-tabs-items>
      </v-tabs>
  </div>
</template>
<script>
import { mapActions, mapState } from "vuex";
export default {
    name: 'dashboard',
    data() {
      return {
        headers: [
          { text: "Id", value: "id", sortable: true },
          { text: "Msg Id", value: "msgId", sortable: true },
          { text: "mdn Response", value: "mdnResponse", sortable: true },
          { text: "msg Data", value: "msgData", sortable: true },
          { text: "Sender Id", value: "senderId", sortable: true },
          { text: "Receiver Id", value: "receiverId", sortable: true },
          { text: "state", value: "state", sortable: true },
          { text: "UpdateDate time", value: "updateDatetime", sortable: true }
        ],
        tab: '',
        loading: false,
        search: '',
        errormsg: '',
        errored: false,
        weekSelect: "",
        items: [{name: 'Past Hour', value: 'interval=1 hour' },
        {name: 'Past 24 Hour', value: 'interval=1 day' },
        {name: 'Past Week', value: 'interval=1 week' },
        {name: 'Date Range', value: 3}],
        startMenu: false,
        lastMenu: false,
        startDate: '',
        lastDate: ''
      }
    },
    watch: {
      async weekSelect(val) {
        if (val !== 3) {
          this.loading = true
          const resp = await this.fetchTableDataByInterval(val)
          if (resp !== true) {
            this.errored = true;
            this.errormsg = res;
          }
          this.loading = false
        }
      },
      tab() {
        this.search = ''
      },
      startDate(val) {
        if (val && this.lastDate) {
          this.getTableData()
        }
      },
      lastDate(val) {
        if (val && this.startDate) {
          this.getTableData()
        }
      }
    },
    mounted() {
      this.weekSelect = 'interval=1 hour'
    },
    computed: {
      ...mapState("dashboardModule", {
        tableData: (state) => state.tableData,
      }),
      filteredData() {
        let sentArray = []
        let recArray = []
        if (this.tableData && this.tableData.length) {
          this.tableData.forEach(el => {
            if (el.direction === 'SEND') {
              sentArray.push(el)
            } else {
              recArray.push(el)
            }
          });
        }
        if (this.tab === 'sent') {
          return sentArray
        } else {
          return recArray
        }
      }
    },
    methods: {
      ...mapActions("dashboardModule", ["fetchTableDataByDate", "fetchTableDataByInterval"]),
      async getTableData() {
        this.loading = true
        const payload = {
          startDate: this.startDate,
          lastDate: this.lastDate
        }
        const res = await this.fetchTableDataByDate(payload)
        if (res !== true) {
          this.errored = true;
          this.errormsg = res;
        }
        this.loading = false
      }
    }
}
</script>
<style scoped>
/*
 * Content
 */

[role="main"] {
  padding-top: 133px; /* Space for fixed navbar */
}

.v-text-field {
  padding: 0;
}

.fixWidth {
  max-width: 180px;
}

.selectableWidth {
  max-width: 200px;
}

@media (min-width: 768px) {
  [role="main"] {
    padding-top: 48px; /* Space for fixed navbar */
  }
}
</style>