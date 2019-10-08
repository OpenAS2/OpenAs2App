<template>
<div id="object-list">
    <section v-if="errored">
        <p>We're sorry, we're not able to retrieve this information at the moment, please try back later</p>
     </section>
     <section v-else>
        <div v-if="loading">Loading...</div>
        <div v-else class="table-responsive">
            <table class="table table-striped table-sm">
            <thead>
                <tr v-if="info[0]">
                <th  v-for="(data,key) in info[0].data" v-html="key"></th>
                <th>Actions</th>
                </tr>
            </thead>
            <tbody v-if="info.length">
                <tr v-for="item in info">
                <td  v-for="(data,key) in item.data" v-html="data" ></td>
                <td> 
                    <span v-for="(action,name) in item.actions" ><router-link :to="action" v-html="name" ></router-link></span>
                </td>
                </tr>
            </tbody>
            <p v-else > No partners found</p> 
            </table>
        </div>
     </section>
</div>
</template>
<script>
export default {
    name: 'object-list',
    data: function() {
        return {
            loading: false,
            info: [],
            errored: false
        };
    },
    props: {
        src: {
            type: Function,
            required: true
        }
    },
    watch: {
        src: function() {
            
        }
    },
    mounted: async function() {
        console.log('Mounting');
        this.loading=true;
        try {
            var r=await this.src();
            console.log('SRC:',r);
            if(r!=null) this.info=r;
        }catch(e) {
            console.log(e);
            this.errored=true;
        } finally {
            this.loading = false;
        }
    }

}
</script>